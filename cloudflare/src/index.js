const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store"
};
const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000;
const NONCE_TTL_MS = 10 * 60 * 1000;
const MAX_JSON_BYTES = 72 * 1024;
const TRANSFER_CODE_TTL_MS = 10 * 60 * 1000;
const MANAGER_WRITE_ROLES = ["OWNER", "OFFICER"];
const QUEST_TYPES = new Set([
  "DAILY_QUEST",
  "WEEKLY_QUEST",
  "MONTHLY_QUEST",
  "REPEATABLE_QUEST",
  "LIMITED_EVENT_QUEST",
  "GUILD_RAID",
  "HIDDEN_QUEST",
  "MAIN_QUEST",
  "SIDE_QUEST",
  "PROMOTION_QUEST",
  "FORMATION_QUEST"
]);
const QUEST_STATUSES = new Set([
  "DRAFT",
  "PUBLISHED",
  "AVAILABLE",
  "ACCEPTED",
  "IN_PROGRESS",
  "SUBMITTED",
  "APPROVED",
  "REJECTED",
  "EXPIRED",
  "CANCELLED"
]);
const FLOW_OWNED_QUEST_STATUSES = new Set([
  "ACCEPTED",
  "IN_PROGRESS",
  "SUBMITTED",
  "APPROVED",
  "REJECTED"
]);
const STRICT_CYCLE_TYPES = new Set(["DAILY_QUEST", "WEEKLY_QUEST", "MONTHLY_QUEST"]);
const PROOF_MODES = new Set(["NONE", "TEXT", "IN_PERSON"]);

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: JSON_HEADERS
  });
}

function error(message, status = 400) {
  return json({ ok: false, error: message }, status);
}

async function readJson(request) {
  const contentType = request.headers.get("content-type") || "";
  if (!contentType.includes("application/json")) {
    throw new Error("application/json required");
  }
  const declaredLength = Number(request.headers.get("content-length") || 0);
  if (declaredLength > MAX_JSON_BYTES) throw new HttpError("request body is too large", 413);
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > MAX_JSON_BYTES) {
    throw new HttpError("request body is too large", 413);
  }
  return JSON.parse(text);
}

function base64ToBytes(value) {
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function bytesToBase64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

async function sha256Base64Url(value) {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  return bytesToBase64Url(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)));
}

function signingHeaders(request) {
  return {
    deviceId: requireText(request.headers.get("x-device-id"), "x-device-id", 128),
    timestamp: Number(requireText(request.headers.get("x-timestamp"), "x-timestamp", 32)),
    nonce: requireText(request.headers.get("x-nonce"), "x-nonce", 128),
    signature: requireText(request.headers.get("x-signature"), "x-signature", 256)
  };
}

async function verifySignature(publicKey, signature, message) {
  const key = await crypto.subtle.importKey(
    "spki",
    base64ToBytes(publicKey),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"]
  );
  return crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    base64ToBytes(signature),
    new TextEncoder().encode(message)
  );
}

async function signedRequestContext(request, env, registrationPublicKey = null) {
  const headers = signingHeaders(request);
  const now = Date.now();
  if (!Number.isFinite(headers.timestamp) || Math.abs(now - headers.timestamp) > MAX_CLOCK_SKEW_MS) {
    throw new HttpError("request timestamp is outside the allowed window", 401);
  }

  const bodyText = await request.clone().text();
  const bodyHash = await sha256Base64Url(bodyText);
  const url = new URL(request.url);
  const canonical = [
    request.method.toUpperCase(),
    url.pathname + url.search,
    String(headers.timestamp),
    headers.nonce,
    bodyHash
  ].join("\n");

  let publicKey = registrationPublicKey;
  if (publicKey == null) {
    const device = await env.DB.prepare(`
      SELECT public_key AS publicKey, revoked_at AS revokedAt
      FROM devices
      WHERE device_id = ?
    `).bind(headers.deviceId).first();
    if (!device || device.revokedAt != null) {
      throw new HttpError("unknown or revoked device", 401);
    }
    publicKey = device.publicKey;
  }

  const expectedDeviceId = await sha256Base64Url(base64ToBytes(publicKey));
  if (headers.deviceId !== expectedDeviceId) {
    throw new HttpError("device id does not match public key", 401);
  }
  if (!await verifySignature(publicKey, headers.signature, canonical)) {
    throw new HttpError("invalid request signature", 401);
  }

  await env.DB.prepare("DELETE FROM replay_nonces WHERE expires_at < ?").bind(now).run();
  try {
    await env.DB.prepare(`
      INSERT INTO replay_nonces (device_id, nonce, used_at, expires_at)
      VALUES (?, ?, ?, ?)
    `).bind(headers.deviceId, headers.nonce, now, now + NONCE_TTL_MS).run();
  } catch {
    throw new HttpError("request nonce has already been used", 409);
  }
  const device = await env.DB.prepare(`
    SELECT user_id AS userId
    FROM devices
    WHERE device_id = ?
  `).bind(headers.deviceId).first();
  return { ...headers, bodyText, userId: device?.userId };
}

class HttpError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

function requireText(value, field, maxLength = 4096) {
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength) {
    throw new Error(`${field} is invalid`);
  }
  return value;
}

function requireUuid(value, field) {
  const text = requireText(value, field, 36).toLowerCase();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(text)) {
    throw new Error(`${field} must be a UUID`);
  }
  return text;
}

function randomTransferCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return bytesToBase64Url(bytes).toUpperCase();
}

async function registerDevice(request, env) {
  const body = await readJson(request.clone());
  const deviceId = requireText(body.deviceId, "deviceId", 128);
  const userId = requireUuid(body.userId, "userId");
  const publicKey = requireText(body.publicKey, "publicKey", 2048);
  const displayName = requireText(body.displayName, "displayName", 80);
  const signed = await signedRequestContext(request, env, publicKey);
  if (signed.deviceId !== deviceId) {
    throw new HttpError("header and body device ids differ", 401);
  }
  const now = Date.now();
  const account = await env.DB.prepare(
    "SELECT user_id AS userId FROM accounts WHERE user_id = ?"
  ).bind(userId).first();
  const existingDevice = await env.DB.prepare(
    "SELECT user_id AS userId, revoked_at AS revokedAt FROM devices WHERE device_id = ?"
  ).bind(deviceId).first();

  if (existingDevice && existingDevice.userId !== userId) {
    throw new HttpError("device is already bound to another account", 409);
  }
  if (existingDevice?.revokedAt != null) {
    throw new HttpError("device has been revoked", 401);
  }

  let transferHash = null;
  if (account && !existingDevice) {
    if (typeof body.transferCode !== "string" || body.transferCode.length === 0) {
      throw new HttpError("account transfer code is required", 401);
    }
    const transferCode = requireText(body.transferCode, "transferCode", 128);
    transferHash = await sha256Base64Url(transferCode.trim().toUpperCase());
  }

  if (transferHash) {
    const results = await env.DB.batch([
      env.DB.prepare(`
        UPDATE account_transfer_codes
        SET used_at = ?, used_by_device_id = ?
        WHERE code_hash = ? AND user_id = ? AND used_at IS NULL AND expires_at >= ?
      `).bind(now, deviceId, transferHash, userId, now),
      env.DB.prepare(
        "UPDATE accounts SET last_seen_at = ? WHERE user_id = ?"
      ).bind(now, userId),
      env.DB.prepare(`
        INSERT INTO devices (
          device_id, user_id, public_key, display_name, created_at, last_seen_at
        )
        SELECT ?, ?, ?, ?, ?, ?
        WHERE EXISTS (
          SELECT 1 FROM account_transfer_codes
          WHERE code_hash = ? AND user_id = ?
            AND used_at = ? AND used_by_device_id = ?
        )
      `).bind(
        deviceId, userId, publicKey, displayName, now, now,
        transferHash, userId, now, deviceId
      )
    ]);
    if (results[0]?.meta?.changes !== 1 || results[2]?.meta?.changes !== 1) {
      throw new HttpError("account transfer code is invalid, expired, or already used", 401);
    }
    return json({ ok: true, userId, deviceId, inherited: true, serverTime: now });
  }

  const statements = [];
  if (!account) {
    statements.push(env.DB.prepare(`
      INSERT INTO accounts (user_id, created_at, last_seen_at) VALUES (?, ?, ?)
    `).bind(userId, now, now));
  } else {
    statements.push(env.DB.prepare(
      "UPDATE accounts SET last_seen_at = ? WHERE user_id = ?"
    ).bind(now, userId));
  }
  statements.push(env.DB.prepare(`
    INSERT INTO devices (
      device_id, user_id, public_key, display_name, created_at, last_seen_at
    ) VALUES (?, ?, ?, ?, ?, ?)
    ON CONFLICT(device_id) DO UPDATE SET
      public_key = excluded.public_key,
      display_name = excluded.display_name,
      last_seen_at = excluded.last_seen_at
  `).bind(deviceId, userId, publicKey, displayName, now, now));
  await env.DB.batch(statements);
  return json({ ok: true, userId, deviceId, inherited: false, serverTime: now });
}

async function createAccountTransfer(env, auth) {
  const now = Date.now();
  const transferCode = randomTransferCode();
  const codeHash = await sha256Base64Url(transferCode);
  await env.DB.prepare(`
    INSERT INTO account_transfer_codes (
      code_hash, user_id, created_by_device_id, created_at, expires_at
    ) VALUES (?, ?, ?, ?, ?)
  `).bind(codeHash, auth.userId, auth.deviceId, now, now + TRANSFER_CODE_TTL_MS).run();
  return json({
    ok: true,
    userId: auth.userId,
    transferCode,
    expiresAt: now + TRANSFER_CODE_TTL_MS
  }, 201);
}

async function listDevices(env, auth) {
  const result = await env.DB.prepare(`
    SELECT device_id AS deviceId, display_name AS displayName,
           created_at AS createdAt, last_seen_at AS lastSeenAt,
           CASE WHEN device_id = ? THEN 1 ELSE 0 END AS currentDevice
    FROM devices
    WHERE user_id = ? AND revoked_at IS NULL
    ORDER BY last_seen_at DESC
  `).bind(auth.deviceId, auth.userId).all();
  return json({ ok: true, devices: result.results });
}

async function revokeDevice(env, auth, targetDeviceId) {
  if (targetDeviceId === auth.deviceId) {
    throw new HttpError("current device cannot revoke itself", 409);
  }
  const result = await env.DB.prepare(`
    UPDATE devices SET revoked_at = ?
    WHERE device_id = ? AND user_id = ? AND revoked_at IS NULL
  `).bind(Date.now(), targetDeviceId, auth.userId).run();
  if (result.meta.changes !== 1) throw new HttpError("active device was not found", 404);
  return json({ ok: true, deviceId: targetDeviceId });
}

async function requireMembership(env, guildId, userId, side = null) {
  const membership = await env.DB.prepare(`
    SELECT
      side,
      profile_id AS profileId,
      display_name AS displayName,
      role_certificate AS roleCertificate
    FROM guild_memberships
    WHERE guild_id = ? AND user_id = ? AND revoked_at IS NULL
  `).bind(guildId, userId).first();
  if (!membership || (side != null && membership.side !== side)) {
    throw new HttpError("guild membership permission denied", 403);
  }
  return membership;
}

async function requireManagerRole(env, guildId, userId, allowedRoles) {
  const membership = await requireMembership(env, guildId, userId, "MANAGER");
  if (!allowedRoles.includes(membership.roleCertificate)) {
    throw new HttpError("manager role permission denied", 403);
  }
  return membership;
}

async function listActiveGuildMemberships(env, guildId) {
  const result = await env.DB.prepare(`
    SELECT
      user_id AS userId,
      profile_id AS profileId,
      side,
      role_certificate AS roleCertificate
    FROM guild_memberships
    WHERE guild_id = ? AND revoked_at IS NULL
  `).bind(guildId).all();
  return result.results || [];
}

function optionalNumber(value, field) {
  if (value == null) return null;
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new Error(`${field} must be a finite number`);
  }
  return value;
}

function optionalStringList(value, field, maxItems = 200) {
  if (value == null) return [];
  if (!Array.isArray(value) || value.length > maxItems) {
    throw new Error(`${field} must be an array`);
  }
  return value.map((entry, index) => requireText(entry, `${field}[${index}]`, 128));
}

function requireNonNegativeNumber(value, field) {
  const number = Number(value || 0);
  if (!Number.isFinite(number) || number < 0) {
    throw new Error(`${field} must be a non-negative number`);
  }
  return number;
}

function validateQuestCatalog(body, guildId, actorMembership, memberships, previousCatalog) {
  if (body.schemaVersion !== 1 || body.guildId !== guildId || !Array.isArray(body.quests)) {
    throw new Error("quest catalog is invalid");
  }
  if (body.quests.length > 500) {
    throw new HttpError("quest catalog contains too many quests", 413);
  }

  const managerIds = new Set();
  const adventurerIds = new Set();
  for (const membership of memberships) {
    if (membership.side === "MANAGER") {
      managerIds.add(membership.userId);
      if (membership.profileId) managerIds.add(membership.profileId);
    } else if (membership.side === "ADVENTURER") {
      adventurerIds.add(membership.userId);
      if (membership.profileId) adventurerIds.add(membership.profileId);
    }
  }
  const previousById = new Map(
    ((previousCatalog && Array.isArray(previousCatalog.quests)) ? previousCatalog.quests : [])
      .filter((quest) => quest && typeof quest === "object" && typeof quest.id === "string")
      .map((quest) => [quest.id, quest])
  );

  const seenQuestIds = new Set();
  const now = Date.now();
  for (const quest of body.quests) {
    if (quest == null || typeof quest !== "object" || Array.isArray(quest)) {
      throw new Error("quest catalog contains an invalid quest");
    }
    const questId = requireText(quest.id, "quest.id", 128);
    if (seenQuestIds.has(questId)) {
      throw new Error("quest catalog contains duplicate quest ids");
    }
    seenQuestIds.add(questId);
    requireText(quest.title, "quest.title", 200);
    if (quest.guildId !== guildId) {
      throw new HttpError("quest belongs to another guild", 409);
    }

    const type = typeof quest.type === "string" ? quest.type : "DAILY_QUEST";
    const status = typeof quest.status === "string" ? quest.status : "DRAFT";
    const proofMode = typeof quest.proofMode === "string" ? quest.proofMode : "TEXT";
    if (!QUEST_TYPES.has(type)) throw new Error(`quest ${questId} has invalid type`);
    if (!QUEST_STATUSES.has(status)) throw new Error(`quest ${questId} has invalid status`);
    if (!PROOF_MODES.has(proofMode)) throw new Error(`quest ${questId} has invalid proof mode`);
    if (type === "GUILD_RAID") throw new Error("guild raid cannot be stored in quest catalog");
    if (FLOW_OWNED_QUEST_STATUSES.has(status)) {
      throw new Error(`quest ${questId} status is controlled by submission flow`);
    }
    requireNonNegativeNumber(quest.gpReward, "quest.gpReward");
    requireNonNegativeNumber(quest.expReward, "quest.expReward");
    requireNonNegativeNumber(quest.bonusGp, "quest.bonusGp");
    requireNonNegativeNumber(quest.bonusExp, "quest.bonusExp");
    requireNonNegativeNumber(quest.penaltyGp, "quest.penaltyGp");
    requireNonNegativeNumber(quest.penaltyExp, "quest.penaltyExp");

    const startsAt = optionalNumber(quest.startsAtMillis, "quest.startsAtMillis");
    const endsAt = optionalNumber(quest.endsAtMillis, "quest.endsAtMillis");
    const announcedAt = optionalNumber(quest.announcedAtMillis, "quest.announcedAtMillis");
    const acceptStartsAt = optionalNumber(quest.acceptStartsAtMillis, "quest.acceptStartsAtMillis");
    if (startsAt != null && endsAt != null && startsAt > endsAt) {
      throw new Error(`quest ${questId} starts after it ends`);
    }
    if (announcedAt != null && acceptStartsAt != null && announcedAt > acceptStartsAt) {
      throw new Error(`quest ${questId} is announced after accepting opens`);
    }
    if (acceptStartsAt != null && endsAt != null && acceptStartsAt > endsAt) {
      throw new Error(`quest ${questId} accepting opens after it ends`);
    }
    if (type === "LIMITED_EVENT_QUEST" && endsAt == null) {
      throw new Error(`quest ${questId} limited event requires an end time`);
    }
    if ((status === "PUBLISHED" || status === "AVAILABLE") && endsAt != null && now > endsAt) {
      throw new Error(`quest ${questId} is already expired and cannot be published`);
    }
    const previous = previousById.get(questId);
    if (
      previous &&
      ["CANCELLED", "EXPIRED"].includes(previous.status) &&
      (status === "PUBLISHED" || status === "AVAILABLE")
    ) {
      throw new Error(`quest ${questId} cannot be republished after ${previous.status}`);
    }

    if (quest.autoReviewEnabled === true) {
      if (type === "PROMOTION_QUEST") {
        throw new Error(`quest ${questId} promotion quest cannot use auto review`);
      }
      if (!MANAGER_WRITE_ROLES.includes(actorMembership.roleCertificate)) {
        throw new HttpError("auto review requires review-capable manager role", 403);
      }
    }

    const reviewerIds = optionalStringList(quest.assignedReviewerIds, "quest.assignedReviewerIds");
    const invalidReviewer = reviewerIds.find((reviewerId) => !managerIds.has(reviewerId));
    if (invalidReviewer) {
      throw new Error(`quest ${questId} assigned reviewer is not an active manager`);
    }
    const adventurerAssignments = optionalStringList(
      quest.assignedAdventurerIds,
      "quest.assignedAdventurerIds"
    );
    const invalidAdventurer = adventurerAssignments.find(
      (adventurerId) => !adventurerIds.has(adventurerId)
    );
    if (invalidAdventurer) {
      throw new Error(`quest ${questId} assigned adventurer is not an active adventurer`);
    }

    const weekdays = Array.isArray(quest.activeWeekdays) ? quest.activeWeekdays : [];
    if (weekdays.some((weekday) => !Number.isInteger(weekday) || weekday < 1 || weekday > 7)) {
      throw new Error(`quest ${questId} has an invalid active weekday`);
    }
    if (
      quest.weeklyRefreshWeekday != null &&
      (!Number.isInteger(quest.weeklyRefreshWeekday) ||
        quest.weeklyRefreshWeekday < 1 ||
        quest.weeklyRefreshWeekday > 7)
    ) {
      throw new Error(`quest ${questId} has an invalid weekly refresh weekday`);
    }
    if (
      quest.monthlyRefreshDay != null &&
      (!Number.isInteger(quest.monthlyRefreshDay) ||
        quest.monthlyRefreshDay < 1 ||
        quest.monthlyRefreshDay > 31)
    ) {
      throw new Error(`quest ${questId} has an invalid monthly refresh day`);
    }
    if (
      type === "REPEATABLE_QUEST" &&
      quest.repeatLimitType != null &&
      quest.repeatLimitType !== "NONE" &&
      (!Number.isInteger(quest.repeatLimitCount) || quest.repeatLimitCount < 1)
    ) {
      throw new Error(`quest ${questId} repeat limit must be greater than zero`);
    }
    if (type === "FORMATION_QUEST") {
      if (!Array.isArray(quest.formationSlots) || quest.formationSlots.length === 0) {
        throw new Error(`quest ${questId} formation quest requires at least one slot`);
      }
      const minimum = Number(quest.formationMinSlotsPerUser || 0);
      const maximum = Number(quest.formationMaxSlotsPerUser || 1);
      if (!Number.isInteger(minimum) || !Number.isInteger(maximum) ||
          minimum < 0 || maximum < 1 || minimum > maximum) {
        throw new Error(`quest ${questId} has invalid formation slot limits`);
      }
    }

    if (STRICT_CYCLE_TYPES.has(type)) {
      const grace = Number(quest.gracePeriodDays || 0);
      const deadline = Number(quest.submissionDeadlineDays || 0);
      if (grace !== 0 || deadline !== 0) {
        throw new Error(`quest ${questId} strict cycle quest cannot use grace or makeup days`);
      }
    }
  }
}

async function managerCanReviewQuest(env, guildId, authUserId, membership, questId) {
  if (membership.side !== "MANAGER") return false;
  if (membership.roleCertificate === "OWNER") return true;
  const stored = await env.DB.prepare(`
    SELECT catalog_json AS catalogJson
    FROM guild_quest_catalogs
    WHERE guild_id = ?
  `).bind(guildId).first();
  const catalog = stored?.catalogJson ? JSON.parse(stored.catalogJson) : null;
  const quest = catalog?.quests?.find((candidate) => candidate?.id === questId);
  if (!quest) return false;
  const reviewerIds = Array.isArray(quest.assignedReviewerIds)
    ? quest.assignedReviewerIds
    : [];
  if (reviewerIds.length > 0) {
    return reviewerIds.includes(authUserId) ||
      (membership.profileId && reviewerIds.includes(membership.profileId));
  }
  return MANAGER_WRITE_ROLES.includes(membership.roleCertificate);
}

function parseCounterSummary(summaryText, action) {
  let summary;
  try {
    summary = JSON.parse(summaryText);
  } catch {
    throw new Error("counter summary must be valid JSON");
  }
  if (summary == null || typeof summary !== "object" || Array.isArray(summary)) {
    throw new Error("counter summary is invalid");
  }
  requireText(summary.questId, "summary.questId", 128);
  requireText(summary.questTitle, "summary.questTitle", 200);
  const proofMode = typeof summary.proofMode === "string" && summary.proofMode.length > 0
    ? summary.proofMode
    : "TEXT";
  if (!PROOF_MODES.has(proofMode)) throw new Error("summary.proofMode is invalid");
  if (action === "SETTLE_SUBMISSION") {
    requireText(summary.submissionId, "summary.submissionId", 128);
    if (typeof summary.approved !== "boolean") throw new Error("summary.approved must be boolean");
    optionalNumber(summary.proposedBonusGp, "summary.proposedBonusGp");
    optionalNumber(summary.proposedBonusExp, "summary.proposedBonusExp");
  }
  return summary;
}

async function updateProfile(request, env, auth) {
  const body = await readJson(request);
  const displayName = requireText(body.displayName, "displayName", 40);
  if (displayName.length < 2) throw new Error("displayName must contain at least 2 characters");
  await env.DB.batch([
    env.DB.prepare(
      "UPDATE accounts SET display_name = ?, last_seen_at = ? WHERE user_id = ?"
    ).bind(displayName, Date.now(), auth.userId),
    env.DB.prepare(
      "UPDATE guild_memberships SET display_name = ? WHERE user_id = ? AND revoked_at IS NULL"
    ).bind(displayName, auth.userId),
    env.DB.prepare(
      "UPDATE guild_join_requests SET applicant_display_name = ? WHERE applicant_user_id = ? AND status = 'PENDING'"
    ).bind(displayName, auth.userId)
  ]);
  return json({ ok: true, userId: auth.userId, displayName });
}

async function createGuild(request, env, auth) {
  const body = await readJson(request);
  const guildId = requireText(body.guildId, "guildId", 128);
  const name = requireText(body.name, "name", 80);
  const inviteCode = requireText(body.inviteCode, "inviteCode", 32).toUpperCase();
  const ownerProfileId = requireText(body.ownerProfileId, "ownerProfileId", 128);
  const ownerDisplayName = requireText(body.ownerDisplayName, "ownerDisplayName", 80);
  const now = Date.now();
  const expiresAt = Math.min(
    Number(body.inviteExpiresAt || now + 30 * 24 * 60 * 60 * 1000),
    now + 90 * 24 * 60 * 60 * 1000
  );

  await env.DB.batch([
    env.DB.prepare(`
      INSERT INTO guilds (guild_id, name, owner_user_id, created_at)
      VALUES (?, ?, ?, ?)
    `).bind(guildId, name, auth.userId, now),
    env.DB.prepare(`
      INSERT INTO guild_memberships (
        guild_id, user_id, profile_id, display_name, side, role_certificate, joined_at
      ) VALUES (?, ?, ?, ?, 'MANAGER', 'OWNER', ?)
    `).bind(guildId, auth.userId, ownerProfileId, ownerDisplayName, now),
    env.DB.prepare(`
      INSERT INTO guild_invites (
        invite_code, guild_id, created_by_user_id, created_at, expires_at
      ) VALUES (?, ?, ?, ?, ?)
    `).bind(inviteCode, guildId, auth.userId, now, expiresAt)
  ]);
  return json({ ok: true, guildId, inviteCode, inviteExpiresAt: expiresAt }, 201);
}

async function createGuildInvite(request, env, auth, guildId) {
  await requireManagerRole(env, guildId, auth.userId, ["OWNER", "OFFICER"]);
  const body = await readJson(request);
  const inviteCode = requireText(body.inviteCode, "inviteCode", 32).toUpperCase();
  const oneTime = body.oneTime === true;
  const replaceReusable = body.replaceReusable === true;
  const now = Date.now();
  const maxLifetime = oneTime ? 7 * 24 * 60 * 60 * 1000 : 90 * 24 * 60 * 60 * 1000;
  const expiresAt = Math.min(Number(body.expiresAt || now + maxLifetime), now + maxLifetime);
  const statements = [];
  if (replaceReusable) {
    statements.push(env.DB.prepare(`
      UPDATE guild_invites
      SET revoked_at = ?
      WHERE guild_id = ? AND one_time = 0 AND revoked_at IS NULL
    `).bind(now, guildId));
  }
  statements.push(env.DB.prepare(`
    INSERT INTO guild_invites (
      invite_code, guild_id, created_by_user_id, created_at, expires_at, one_time
    ) VALUES (?, ?, ?, ?, ?, ?)
  `).bind(inviteCode, guildId, auth.userId, now, expiresAt, oneTime ? 1 : 0));
  await env.DB.batch(statements);
  return json({ ok: true, guildId, inviteCode, oneTime, expiresAt }, 201);
}

async function resolveInvite(url, env) {
  const code = requireText(url.searchParams.get("code"), "code", 32).toUpperCase();
  const now = Date.now();
  const invite = await env.DB.prepare(`
    SELECT
      i.guild_id AS guildId,
      g.name,
      i.expires_at AS expiresAt
    FROM guild_invites i
    JOIN guilds g ON g.guild_id = i.guild_id
    WHERE i.invite_code = ?
      AND i.revoked_at IS NULL
      AND i.expires_at >= ?
      AND (i.one_time = 0 OR i.used_at IS NULL)
      AND g.archived_at IS NULL
  `).bind(code, now).first();
  if (!invite) throw new HttpError("invite code was not found or has expired", 404);
  return json({ ok: true, guild: invite });
}

async function requestGuildJoin(request, env, auth) {
  const body = await readJson(request);
  const inviteCode = requireText(body.inviteCode, "inviteCode", 32).toUpperCase();
  const requestedSide = requireText(body.requestedSide, "requestedSide", 16).toUpperCase();
  if (!["MANAGER", "ADVENTURER"].includes(requestedSide)) {
    throw new Error("requestedSide is invalid");
  }
  const now = Date.now();
  const invite = await env.DB.prepare(`
    SELECT guild_id AS guildId, one_time AS oneTime
    FROM guild_invites
    WHERE invite_code = ?
      AND revoked_at IS NULL
      AND expires_at >= ?
      AND (one_time = 0 OR used_at IS NULL)
  `).bind(inviteCode, now).first();
  if (!invite) throw new HttpError("invite code was not found or has expired", 404);

  const existing = await env.DB.prepare(`
    SELECT side
    FROM guild_memberships
    WHERE guild_id = ? AND user_id = ? AND revoked_at IS NULL
  `).bind(invite.guildId, auth.userId).first();
  if (existing) {
    throw new HttpError(
      existing.side === requestedSide
        ? "user is already a guild member"
        : "user cannot belong to both sides of the same guild",
      409
    );
  }

  const requestId = requireText(body.requestId, "requestId", 128);
  if (invite.oneTime === 1) {
    const consumed = await env.DB.prepare(`
      UPDATE guild_invites
      SET used_at = ?, used_by_user_id = ?
      WHERE invite_code = ? AND one_time = 1 AND used_at IS NULL
    `).bind(now, auth.userId, inviteCode).run();
    if (consumed.meta.changes !== 1) {
      throw new HttpError("one-time invite has already been used", 409);
    }
  }
  await env.DB.prepare(`
    INSERT INTO guild_join_requests (
      request_id, guild_id, applicant_user_id, applicant_profile_id,
      applicant_display_name, requested_side, status, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
    ON CONFLICT(guild_id, applicant_user_id) DO UPDATE SET
      request_id = excluded.request_id,
      applicant_profile_id = excluded.applicant_profile_id,
      applicant_display_name = excluded.applicant_display_name,
      requested_side = excluded.requested_side,
      status = 'PENDING',
      created_at = excluded.created_at,
      reviewed_at = NULL,
      reviewed_by_user_id = NULL
  `).bind(
    requestId,
    invite.guildId,
    auth.userId,
    requireText(body.applicantProfileId, "applicantProfileId", 128),
    requireText(body.applicantDisplayName, "applicantDisplayName", 80),
    requestedSide,
    now
  ).run();
  return json({ ok: true, requestId, guildId: invite.guildId, status: "PENDING" }, 201);
}

async function listJoinRequests(url, env, auth) {
  const guildId = requireText(url.searchParams.get("guildId"), "guildId", 128);
  await requireManagerRole(env, guildId, auth.userId, ["OWNER", "OFFICER"]);
  const result = await env.DB.prepare(`
    SELECT
      request_id AS requestId,
      applicant_user_id AS applicantUserId,
      applicant_profile_id AS applicantProfileId,
      applicant_display_name AS applicantDisplayName,
      requested_side AS requestedSide,
      created_at AS createdAt
    FROM guild_join_requests
    WHERE guild_id = ? AND status = 'PENDING'
    ORDER BY created_at ASC
    LIMIT 100
  `).bind(guildId).all();
  return json({ ok: true, requests: result.results });
}

async function decideJoinRequest(request, env, auth, requestId) {
  const body = await readJson(request);
  const decision = requireText(body.decision, "decision", 16).toUpperCase();
  if (!["APPROVED", "REJECTED"].includes(decision)) {
    throw new Error("decision is invalid");
  }
  const pending = await env.DB.prepare(`
    SELECT
      guild_id AS guildId,
      applicant_user_id AS applicantUserId,
      requested_side AS requestedSide
    FROM guild_join_requests
    WHERE request_id = ? AND status = 'PENDING'
  `).bind(requestId).first();
  if (!pending) throw new HttpError("pending join request was not found", 404);
  await requireManagerRole(env, pending.guildId, auth.userId, ["OWNER", "OFFICER"]);
  const now = Date.now();

  const statements = [
    env.DB.prepare(`
      UPDATE guild_join_requests
      SET status = ?, reviewed_at = ?, reviewed_by_user_id = ?
      WHERE request_id = ? AND status = 'PENDING'
    `).bind(decision, now, auth.userId, requestId)
  ];
  if (decision === "APPROVED") {
    statements.push(
      env.DB.prepare(`
        INSERT INTO guild_memberships (
          guild_id, user_id, profile_id, display_name, side, role_certificate, joined_at
        ) SELECT ?, ?, applicant_profile_id, applicant_display_name, ?, ?, ?
          FROM guild_join_requests
          WHERE request_id = ? AND status = 'APPROVED'
        ON CONFLICT(guild_id, user_id) DO UPDATE SET
          profile_id = excluded.profile_id,
          display_name = excluded.display_name,
          side = excluded.side,
          role_certificate = excluded.role_certificate,
          joined_at = excluded.joined_at,
          revoked_at = NULL
      `).bind(
        pending.guildId,
        pending.applicantUserId,
        pending.requestedSide,
        pending.requestedSide === "MANAGER" ? "TRAINEE" : "MEMBER",
        now,
        requestId
      )
    );
  }
  await env.DB.batch(statements);
  return json({ ok: true, requestId, status: decision });
}

async function revokeGuildMember(env, auth, guildId, memberUserId) {
  await requireManagerRole(env, guildId, auth.userId, ["OWNER"]);
  const guild = await env.DB.prepare(
    "SELECT owner_user_id AS ownerUserId FROM guilds WHERE guild_id = ?"
  ).bind(guildId).first();
  if (!guild) throw new HttpError("guild was not found", 404);
  if (guild.ownerUserId === memberUserId) throw new HttpError("guild owner cannot be removed", 409);
  const result = await env.DB.prepare(`
    UPDATE guild_memberships SET revoked_at = ?
    WHERE guild_id = ? AND user_id = ? AND revoked_at IS NULL
  `).bind(Date.now(), guildId, memberUserId).run();
  if (result.meta.changes !== 1) throw new HttpError("active guild member was not found", 404);
  await env.DB.prepare(`
    UPDATE counter_sessions
    SET status = 'CANCELLED', completed_at = ?
    WHERE guild_id = ?
      AND status IN ('WAITING_FOR_COUNTERPART', 'AWAITING_FINAL_CONFIRMATION')
      AND (adventurer_user_id = ? OR manager_user_id = ?)
  `).bind(Date.now(), guildId, memberUserId, memberUserId).run();
  return json({ ok: true, guildId, memberUserId });
}

async function listMyGuilds(env, auth) {
  const result = await env.DB.prepare(`
    SELECT
      g.guild_id AS guildId,
      g.name,
      m.side,
      m.profile_id AS profileId,
      m.display_name AS displayName,
      m.role_certificate AS roleCertificate,
      m.joined_at AS joinedAt,
      CASE WHEN m.side = 'MANAGER' THEN (
        SELECT i.invite_code
        FROM guild_invites i
        WHERE i.guild_id = g.guild_id
          AND i.one_time = 0
          AND i.revoked_at IS NULL
          AND i.expires_at >= ?
        ORDER BY i.created_at DESC
        LIMIT 1
      ) ELSE NULL END AS inviteCode
    FROM guild_memberships m
    JOIN guilds g ON g.guild_id = m.guild_id
    WHERE m.user_id = ? AND m.revoked_at IS NULL AND g.archived_at IS NULL
    ORDER BY m.joined_at ASC
  `).bind(Date.now(), auth.userId).all();
  return json({ ok: true, guilds: result.results });
}

async function getGuildQuestCatalog(env, auth, guildId) {
  await requireMembership(env, guildId, auth.userId);
  const stored = await env.DB.prepare(`
    SELECT catalog_json AS catalogJson, revision, updated_at AS updatedAt
    FROM guild_quest_catalogs
    WHERE guild_id = ?
  `).bind(guildId).first();
  if (!stored) {
    return json({ ok: true, guildId, catalog: null, revision: 0, updatedAt: null });
  }
  return json({
    ok: true,
    guildId,
    catalog: JSON.parse(stored.catalogJson),
    revision: stored.revision,
    updatedAt: stored.updatedAt
  });
}

async function putGuildQuestCatalog(request, env, auth, guildId) {
  const actorMembership = await requireManagerRole(env, guildId, auth.userId, MANAGER_WRITE_ROLES);
  const body = await readJson(request);
  const memberships = await listActiveGuildMemberships(env, guildId);
  const previous = await env.DB.prepare(`
    SELECT catalog_json AS catalogJson
    FROM guild_quest_catalogs
    WHERE guild_id = ?
  `).bind(guildId).first();
  const previousCatalog = previous?.catalogJson ? JSON.parse(previous.catalogJson) : null;
  validateQuestCatalog(body, guildId, actorMembership, memberships, previousCatalog);
  const catalogJson = JSON.stringify({
    schemaVersion: 1,
    guildId,
    quests: body.quests
  });
  if (new TextEncoder().encode(catalogJson).byteLength > 64 * 1024) {
    throw new HttpError("quest catalog is too large", 413);
  }
  const now = Date.now();
  await env.DB.prepare(`
    INSERT INTO guild_quest_catalogs (
      guild_id, catalog_json, revision, updated_at, updated_by_user_id
    ) VALUES (?, ?, 1, ?, ?)
    ON CONFLICT(guild_id) DO UPDATE SET
      catalog_json = excluded.catalog_json,
      revision = guild_quest_catalogs.revision + 1,
      updated_at = excluded.updated_at,
      updated_by_user_id = excluded.updated_by_user_id
  `).bind(guildId, catalogJson, now, auth.userId).run();
  const stored = await env.DB.prepare(
    "SELECT revision FROM guild_quest_catalogs WHERE guild_id = ?"
  ).bind(guildId).first();
  return json({
    ok: true,
    guildId,
    revision: stored.revision,
    updatedAt: now
  });
}

async function createCounterSession(request, env, auth) {
  const body = await readJson(request);
  const now = Date.now();
  const expiresAt = Math.min(
    Number(body.expiresAt || now + 10 * 60 * 1000),
    now + 10 * 60 * 1000
  );
  const guildId = requireText(body.guildId, "guildId", 128);
  const action = requireText(body.action, "action", 64);
  if (!["ACCEPT_QUEST", "SUBMIT_QUEST", "SETTLE_SUBMISSION"].includes(action)) {
    throw new Error("action is invalid");
  }
  const actorMembership = await requireMembership(env, guildId, auth.userId);
  const managerInitiated = action === "SETTLE_SUBMISSION";
  if ((managerInitiated && actorMembership.side !== "MANAGER") ||
      (!managerInitiated && actorMembership.side !== "ADVENTURER")) {
    throw new HttpError("counter action is not allowed for this guild side", 403);
  }
  const encryptedSummary = requireText(body.encryptedSummary, "encryptedSummary", 16384);
  const summary = parseCounterSummary(encryptedSummary, action);
  if (
    managerInitiated &&
    !await managerCanReviewQuest(env, guildId, auth.userId, actorMembership, summary.questId)
  ) {
    throw new HttpError("manager cannot settle this quest submission", 403);
  }
  let adventurerUserId = auth.userId;
  if (managerInitiated) {
    adventurerUserId = requireText(body.adventurerUserId, "adventurerUserId", 128);
    await requireMembership(env, guildId, adventurerUserId, "ADVENTURER");
    if (summary.adventurerCloudUserId != null && summary.adventurerCloudUserId !== adventurerUserId) {
      throw new HttpError("summary adventurer does not match settlement target", 409);
    }
  }
  const initialStatus = managerInitiated
    ? "AWAITING_FINAL_CONFIRMATION"
    : "WAITING_FOR_COUNTERPART";

  await env.DB.prepare(`
    INSERT INTO counter_sessions (
      session_id, guild_id, action, adventurer_user_id, manager_user_id,
      status, nonce_hash, encrypted_summary, created_at, expires_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(
    requireText(body.sessionId, "sessionId", 128),
    guildId,
    action,
    adventurerUserId,
    managerInitiated ? auth.userId : null,
    initialStatus,
    requireText(body.nonceHash, "nonceHash", 128),
    encryptedSummary,
    now,
    expiresAt
  ).run();

  return json({ ok: true, status: initialStatus, expiresAt }, 201);
}

async function listCounterSessions(url, env, auth) {
  const guildId = requireText(url.searchParams.get("guildId"), "guildId", 128);
  const membership = await requireMembership(env, guildId, auth.userId);
  const now = Date.now();
  const result = await env.DB.prepare(`
    SELECT
      session_id AS sessionId,
      guild_id AS guildId,
      action,
      adventurer_user_id AS adventurerUserId,
      manager_user_id AS managerUserId,
      status,
      encrypted_summary AS encryptedSummary,
      created_at AS createdAt,
      expires_at AS expiresAt,
      completed_at AS completedAt
    FROM counter_sessions
    WHERE guild_id = ?
      AND (
        (status IN ('WAITING_FOR_COUNTERPART', 'AWAITING_FINAL_CONFIRMATION') AND expires_at >= ?)
        OR (status IN ('COMPLETED', 'CANCELLED') AND completed_at >= ?)
      )
      AND (? = 'MANAGER' OR adventurer_user_id = ?)
    ORDER BY created_at ASC
    LIMIT 100
  `).bind(guildId, now, now - 24 * 60 * 60 * 1000, membership.side, auth.userId).all();

  if (membership.side !== "MANAGER") {
    return json({ ok: true, sessions: result.results });
  }
  const visibleSessions = [];
  for (const session of result.results) {
    if (session.action === "ACCEPT_QUEST") {
      if (MANAGER_WRITE_ROLES.includes(membership.roleCertificate)) {
        visibleSessions.push(session);
      }
      continue;
    }
    const summary = parseCounterSummary(session.encryptedSummary, session.action);
    if (await managerCanReviewQuest(env, guildId, auth.userId, membership, summary.questId)) {
      visibleSessions.push(session);
    }
  }
  return json({ ok: true, sessions: visibleSessions });
}

async function confirmCounterSession(env, auth, sessionId) {
  const session = await env.DB.prepare(`
    SELECT
      guild_id AS guildId,
      action,
      adventurer_user_id AS adventurerUserId,
      status,
      expires_at AS expiresAt
    FROM counter_sessions
    WHERE session_id = ?
  `).bind(sessionId).first();
  if (!session) throw new HttpError("counter session was not found", 404);
  if (session.expiresAt < Date.now()) throw new HttpError("counter session has expired", 409);
  const membership = await requireMembership(env, session.guildId, auth.userId);
  let managerCanConfirm = false;
  if (
    ["ACCEPT_QUEST", "SUBMIT_QUEST"].includes(session.action) &&
    session.status === "WAITING_FOR_COUNTERPART" &&
    membership.side === "MANAGER"
  ) {
    if (session.action === "ACCEPT_QUEST") {
      managerCanConfirm = MANAGER_WRITE_ROLES.includes(membership.roleCertificate);
    } else {
      const storedSummary = await env.DB.prepare(`
        SELECT encrypted_summary AS encryptedSummary
        FROM counter_sessions
        WHERE session_id = ?
      `).bind(sessionId).first();
      const summary = parseCounterSummary(storedSummary.encryptedSummary, session.action);
      managerCanConfirm = await managerCanReviewQuest(
        env,
        session.guildId,
        auth.userId,
        membership,
        summary.questId
      );
    }
  }
  const managerConfirmation =
    managerCanConfirm;
  const adventurerConfirmation =
    session.action === "SETTLE_SUBMISSION" &&
    session.status === "AWAITING_FINAL_CONFIRMATION" &&
    membership.side === "ADVENTURER" &&
    session.adventurerUserId === auth.userId;
  if (!managerConfirmation && !adventurerConfirmation) {
    throw new HttpError("this device cannot confirm the current counter stage", 403);
  }
  const now = Date.now();
  const result = await env.DB.prepare(`
    UPDATE counter_sessions
    SET
      manager_user_id = CASE WHEN ? THEN ? ELSE manager_user_id END,
      status = 'COMPLETED',
      completed_at = ?
    WHERE session_id = ? AND status = ?
  `).bind(
    managerConfirmation ? 1 : 0,
    auth.userId,
    now,
    sessionId,
    session.status
  ).run();
  if (result.meta.changes !== 1) {
    throw new HttpError("counter session changed concurrently", 409);
  }
  return json({ ok: true, sessionId, status: "COMPLETED", completedAt: now });
}

async function cancelCounterSession(env, auth, sessionId) {
  const session = await env.DB.prepare(`
    SELECT
      guild_id AS guildId,
      adventurer_user_id AS adventurerUserId,
      manager_user_id AS managerUserId,
      status
    FROM counter_sessions
    WHERE session_id = ?
  `).bind(sessionId).first();
  if (!session) throw new HttpError("counter session was not found", 404);
  const membership = await requireMembership(env, session.guildId, auth.userId);
  const participant =
    auth.userId === session.adventurerUserId ||
    auth.userId === session.managerUserId ||
    membership.side === "MANAGER" &&
    MANAGER_WRITE_ROLES.includes(membership.roleCertificate);
  if (!participant) throw new HttpError("only a participant can cancel this counter session", 403);
  if (!["WAITING_FOR_COUNTERPART", "AWAITING_FINAL_CONFIRMATION"].includes(session.status)) {
    throw new HttpError("counter session is no longer active", 409);
  }
  await env.DB.prepare(`
    UPDATE counter_sessions
    SET status = 'CANCELLED', completed_at = ?
    WHERE session_id = ?
  `).bind(Date.now(), sessionId).run();
  return json({ ok: true, sessionId, status: "CANCELLED" });
}

async function putMailboxEvent(request, env, auth) {
  const body = await readJson(request);
  const now = Date.now();
  const expiresAt = Math.min(
    Number(body.expiresAt || now + 7 * 24 * 60 * 60 * 1000),
    now + 30 * 24 * 60 * 60 * 1000
  );

  const guildId = requireText(body.guildId, "guildId", 128);
  const recipientUserId = requireText(body.recipientUserId, "recipientUserId", 128);
  const senderDeviceId = requireText(body.senderDeviceId, "senderDeviceId", 128);
  if (senderDeviceId !== auth.deviceId) throw new HttpError("sender device is invalid", 403);
  await requireMembership(env, guildId, auth.userId);
  await requireMembership(env, guildId, recipientUserId);

  await env.DB.prepare(`
    INSERT INTO encrypted_mailbox_events (
      event_id, guild_id, recipient_user_id, sender_device_id,
      event_type, ciphertext, created_at, expires_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(
    requireText(body.eventId, "eventId", 128),
    guildId,
    recipientUserId,
    senderDeviceId,
    requireText(body.eventType, "eventType", 64),
    requireText(body.ciphertext, "ciphertext", 65536),
    now,
    expiresAt
  ).run();

  return json({ ok: true, expiresAt }, 201);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    try {
      if (request.method === "GET" && url.pathname === "/health") {
        return json({
          ok: true,
          service: "adventurer-guild-api",
          version: "0.1.0",
          serverTime: Date.now()
        });
      }
      if (request.method === "POST" && url.pathname === "/v1/devices/register") {
        return await registerDevice(request, env);
      }
      let auth = null;
      if (url.pathname.startsWith("/v1/")) auth = await signedRequestContext(request, env);
      if (request.method === "POST" && url.pathname === "/v1/me/account-transfer") {
        return await createAccountTransfer(env, auth);
      }
      if (request.method === "PATCH" && url.pathname === "/v1/me/profile") {
        return await updateProfile(request, env, auth);
      }
      if (request.method === "GET" && url.pathname === "/v1/me/devices") {
        return await listDevices(env, auth);
      }
      const deviceRevokeMatch = url.pathname.match(/^\/v1\/me\/devices\/([^/]+)\/revoke$/);
      if (request.method === "POST" && deviceRevokeMatch) {
        return await revokeDevice(env, auth, decodeURIComponent(deviceRevokeMatch[1]));
      }
      if (request.method === "POST" && url.pathname === "/v1/guilds") {
        return await createGuild(request, env, auth);
      }
      const guildInviteCreateMatch = url.pathname.match(/^\/v1\/guilds\/([^/]+)\/invites$/);
      if (request.method === "POST" && guildInviteCreateMatch) {
        return await createGuildInvite(
          request,
          env,
          auth,
          decodeURIComponent(guildInviteCreateMatch[1])
        );
      }
      if (request.method === "GET" && url.pathname === "/v1/guild-invites/resolve") {
        return await resolveInvite(url, env);
      }
      if (request.method === "POST" && url.pathname === "/v1/guild-join-requests") {
        return await requestGuildJoin(request, env, auth);
      }
      if (request.method === "GET" && url.pathname === "/v1/guild-join-requests") {
        return await listJoinRequests(url, env, auth);
      }
      const joinDecisionMatch = url.pathname.match(/^\/v1\/guild-join-requests\/([^/]+)\/decision$/);
      if (request.method === "POST" && joinDecisionMatch) {
        return await decideJoinRequest(request, env, auth, decodeURIComponent(joinDecisionMatch[1]));
      }
      if (request.method === "GET" && url.pathname === "/v1/me/guilds") {
        return await listMyGuilds(env, auth);
      }
      const questCatalogMatch = url.pathname.match(/^\/v1\/guilds\/([^/]+)\/quest-catalog$/);
      if (request.method === "GET" && questCatalogMatch) {
        return await getGuildQuestCatalog(
          env,
          auth,
          decodeURIComponent(questCatalogMatch[1])
        );
      }
      if (request.method === "PUT" && questCatalogMatch) {
        return await putGuildQuestCatalog(
          request,
          env,
          auth,
          decodeURIComponent(questCatalogMatch[1])
        );
      }
      const memberRevokeMatch = url.pathname.match(/^\/v1\/guilds\/([^/]+)\/members\/([^/]+)\/revoke$/);
      if (request.method === "POST" && memberRevokeMatch) {
        return await revokeGuildMember(
          env,
          auth,
          decodeURIComponent(memberRevokeMatch[1]),
          decodeURIComponent(memberRevokeMatch[2])
        );
      }
      if (request.method === "POST" && url.pathname === "/v1/counter-sessions") {
        return await createCounterSession(request, env, auth);
      }
      if (request.method === "GET" && url.pathname === "/v1/counter-sessions") {
        return await listCounterSessions(url, env, auth);
      }
      const counterConfirmMatch = url.pathname.match(/^\/v1\/counter-sessions\/([^/]+)\/confirm$/);
      if (request.method === "POST" && counterConfirmMatch) {
        return await confirmCounterSession(env, auth, decodeURIComponent(counterConfirmMatch[1]));
      }
      const counterCancelMatch = url.pathname.match(/^\/v1\/counter-sessions\/([^/]+)\/cancel$/);
      if (request.method === "POST" && counterCancelMatch) {
        return await cancelCounterSession(env, auth, decodeURIComponent(counterCancelMatch[1]));
      }
      if (request.method === "POST" && url.pathname === "/v1/mailbox/events") {
        return await putMailboxEvent(request, env, auth);
      }
      return error("not found", 404);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "request failed";
      return error(message, cause instanceof HttpError ? cause.status : 400);
    }
  }
};
