import {
  createHash,
  generateKeyPairSync,
  randomBytes,
  randomUUID,
  sign
} from "node:crypto";

const baseUrl = process.env.TEST_BASE_URL || "http://127.0.0.1:8787";

function identity(userId, displayName) {
  const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicKeyDer = publicKey.export({ type: "spki", format: "der" });
  return {
    userId,
    displayName,
    privateKey,
    publicKey: publicKeyDer.toString("base64"),
    deviceId: createHash("sha256").update(publicKeyDer).digest("base64url")
  };
}

async function signedRequest(actor, method, path, bodyObject = null, expectedStatus = 200) {
  const body = bodyObject == null ? "" : JSON.stringify(bodyObject);
  const timestamp = Date.now().toString();
  const nonce = randomBytes(24).toString("base64url");
  const bodyHash = createHash("sha256").update(body).digest("base64url");
  const canonical = [method, path, timestamp, nonce, bodyHash].join("\n");
  const signature = sign("sha256", Buffer.from(canonical), {
    key: actor.privateKey,
    dsaEncoding: "ieee-p1363"
  }).toString("base64");
  const response = await fetch(baseUrl + path, {
    method,
    headers: {
      accept: "application/json",
      ...(body ? { "content-type": "application/json" } : {}),
      "x-device-id": actor.deviceId,
      "x-timestamp": timestamp,
      "x-nonce": nonce,
      "x-signature": signature
    },
    body: body || undefined
  });
  const result = await response.json();
  if (response.status !== expectedStatus) {
    throw new Error(`${method} ${path}: expected ${expectedStatus}, got ${response.status}: ${JSON.stringify(result)}`);
  }
  return result;
}

async function register(actor) {
  return signedRequest(actor, "POST", "/v1/devices/register", {
    deviceId: actor.deviceId,
    userId: actor.userId,
    publicKey: actor.publicKey,
    displayName: actor.displayName
  });
}

const suffix = Date.now().toString(36);
const owner = identity(randomUUID(), "Flow Owner");
const adventurer = identity(randomUUID(), "Flow Adventurer");
const trainee = identity(randomUUID(), "Flow Trainee");
const oneTimeGuest = identity(randomUUID(), "One Time Guest");
const lateGuest = identity(randomUUID(), "Late Guest");
const guildId = `flow-guild-${suffix}`;
const inviteCode = `F${suffix.slice(-7)}`.toUpperCase();
const joinRequestId = randomUUID();

await register(owner);
await register(adventurer);
await register(trainee);
await register(oneTimeGuest);
await register(lateGuest);
await signedRequest(owner, "POST", "/v1/guilds", {
  guildId,
  name: "Guild flow smoke test",
  inviteCode,
  inviteExpiresAt: Date.now() + 60 * 60 * 1000,
  ownerProfileId: `owner-profile-${suffix}`,
  ownerDisplayName: owner.displayName
}, 201);
await signedRequest(owner, "PATCH", "/v1/me/profile", {
  displayName: "Renamed Guild Owner"
});
const ownerMemberships = await signedRequest(owner, "GET", "/v1/me/guilds");
if (ownerMemberships.guilds[0]?.displayName !== "Renamed Guild Owner") {
  throw new Error(`Profile name was not propagated: ${JSON.stringify(ownerMemberships)}`);
}
await signedRequest(adventurer, "GET", `/v1/guild-invites/resolve?code=${inviteCode}`);
await signedRequest(adventurer, "POST", "/v1/guild-join-requests", {
  requestId: joinRequestId,
  inviteCode,
  applicantProfileId: `local-profile-${suffix}`,
  applicantDisplayName: adventurer.displayName,
  requestedSide: "ADVENTURER"
}, 201);
const pending = await signedRequest(owner, "GET", `/v1/guild-join-requests?guildId=${guildId}`);
if (pending.requests.length !== 1 || pending.requests[0].requestId !== joinRequestId) {
  throw new Error(`Pending request mismatch: ${JSON.stringify(pending)}`);
}
await signedRequest(owner, "POST", `/v1/guild-join-requests/${joinRequestId}/decision`, {
  decision: "APPROVED"
});
const memberships = await signedRequest(adventurer, "GET", "/v1/me/guilds");
if (!memberships.guilds.some((guild) => guild.guildId === guildId && guild.side === "ADVENTURER")) {
  throw new Error(`Approved membership was not returned: ${JSON.stringify(memberships)}`);
}
const questCatalog = {
  schemaVersion: 1,
  guildId,
  quests: [{
    id: `quest-${suffix}`,
    guildId,
    title: "Shared quest",
    description: "Visible to every approved guild member.",
    type: "SIDE_QUEST",
    status: "PUBLISHED"
  }]
};
await signedRequest(owner, "PUT", `/v1/guilds/${guildId}/quest-catalog`, questCatalog);
const adventurerCatalog = await signedRequest(
  adventurer,
  "GET",
  `/v1/guilds/${guildId}/quest-catalog`
);
if (adventurerCatalog.catalog?.quests?.[0]?.title !== "Shared quest") {
  throw new Error(`Quest catalog mismatch: ${JSON.stringify(adventurerCatalog)}`);
}
await signedRequest(owner, "PUT", `/v1/guilds/${guildId}/quest-catalog`, {
  schemaVersion: 1,
  guildId,
  quests: [{
    id: `bad-promotion-${suffix}`,
    guildId,
    title: "Unsafe promotion",
    type: "PROMOTION_QUEST",
    status: "PUBLISHED",
    autoReviewEnabled: true
  }]
}, 400);
await signedRequest(owner, "PUT", `/v1/guilds/${guildId}/quest-catalog`, {
  schemaVersion: 1,
  guildId,
  quests: [{
    id: `bad-reviewer-${suffix}`,
    guildId,
    title: "Invalid reviewer",
    type: "SIDE_QUEST",
    status: "PUBLISHED",
    assignedReviewerIds: ["not-a-manager"]
  }]
}, 400);
await signedRequest(owner, "PUT", `/v1/guilds/${guildId}/quest-catalog`, {
  schemaVersion: 1,
  guildId,
  quests: [{
    id: `cancelled-${suffix}`,
    guildId,
    title: "Cancelled quest",
    type: "SIDE_QUEST",
    status: "CANCELLED"
  }]
});
await signedRequest(owner, "PUT", `/v1/guilds/${guildId}/quest-catalog`, {
  schemaVersion: 1,
  guildId,
  quests: [{
    id: `cancelled-${suffix}`,
    guildId,
    title: "Cancelled quest",
    type: "SIDE_QUEST",
    status: "PUBLISHED"
  }]
}, 400);
const conflict = await signedRequest(adventurer, "POST", "/v1/guild-join-requests", {
  requestId: randomUUID(),
  inviteCode,
  applicantProfileId: `local-profile-${suffix}`,
  applicantDisplayName: adventurer.displayName,
  requestedSide: "MANAGER"
}, 409);

const traineeRequestId = randomUUID();
await signedRequest(trainee, "POST", "/v1/guild-join-requests", {
  requestId: traineeRequestId,
  inviteCode,
  applicantProfileId: `trainee-profile-${suffix}`,
  applicantDisplayName: trainee.displayName,
  requestedSide: "MANAGER"
}, 201);
await signedRequest(owner, "POST", `/v1/guild-join-requests/${traineeRequestId}/decision`, {
  decision: "APPROVED"
});
await signedRequest(trainee, "GET", `/v1/guild-join-requests?guildId=${guildId}`, null, 403);
await signedRequest(
  trainee,
  "PUT",
  `/v1/guilds/${guildId}/quest-catalog`,
  questCatalog,
  403
);
await signedRequest(trainee, "POST", "/v1/counter-sessions", {
  sessionId: `forbidden-settlement-${suffix}`,
  guildId,
  action: "SETTLE_SUBMISSION",
  adventurerUserId: adventurer.userId,
  nonceHash: createHash("sha256").update(randomBytes(32)).digest("base64url"),
  encryptedSummary: JSON.stringify({
    questId: `cancelled-${suffix}`,
    questTitle: "Cancelled quest",
    submissionId: `forbidden-submission-${suffix}`,
    approved: true,
    proofMode: "TEXT"
  }),
  expiresAt: Date.now() + 60_000
}, 403);

const oneTimeInviteCode = `O${suffix.slice(-7)}`.toUpperCase();
await signedRequest(owner, "POST", `/v1/guilds/${guildId}/invites`, {
  inviteCode: oneTimeInviteCode,
  oneTime: true,
  expiresAt: Date.now() + 60 * 60 * 1000,
  replaceReusable: false
}, 201);
await signedRequest(oneTimeGuest, "POST", "/v1/guild-join-requests", {
  requestId: randomUUID(),
  inviteCode: oneTimeInviteCode,
  applicantProfileId: `one-time-profile-${suffix}`,
  applicantDisplayName: oneTimeGuest.displayName,
  requestedSide: "ADVENTURER"
}, 201);
await signedRequest(lateGuest, "GET", `/v1/guild-invites/resolve?code=${oneTimeInviteCode}`, null, 404);

const replacementInviteCode = `R${suffix.slice(-7)}`.toUpperCase();
await signedRequest(owner, "POST", `/v1/guilds/${guildId}/invites`, {
  inviteCode: replacementInviteCode,
  oneTime: false,
  expiresAt: Date.now() + 60 * 60 * 1000,
  replaceReusable: true
}, 201);
await signedRequest(lateGuest, "GET", `/v1/guild-invites/resolve?code=${inviteCode}`, null, 404);
await signedRequest(lateGuest, "GET", `/v1/guild-invites/resolve?code=${replacementInviteCode}`);

await signedRequest(
  owner,
  "POST",
  `/v1/guilds/${guildId}/members/${adventurer.userId}/revoke`,
  {}
);
const revokedMemberships = await signedRequest(adventurer, "GET", "/v1/me/guilds");
if (revokedMemberships.guilds.some((guild) => guild.guildId === guildId)) {
  throw new Error(`Revoked membership is still visible: ${JSON.stringify(revokedMemberships)}`);
}
await signedRequest(adventurer, "GET", `/v1/counter-sessions?guildId=${guildId}`, null, 403);

console.log(JSON.stringify({
  ok: true,
  guildId,
  ownerUserId: owner.userId,
  adventurerUserId: adventurer.userId,
  approvedSide: "ADVENTURER",
  oppositeSideRejected: conflict.error,
  traineePrivilegeEscalationRejected: true,
  profileRenamePropagated: true,
  questCatalogShared: true,
  traineeQuestCatalogWriteRejected: true,
  oneTimeInviteConsumed: true,
  reusableInviteRotated: true,
  revokedMemberAccessRejected: true
}, null, 2));
