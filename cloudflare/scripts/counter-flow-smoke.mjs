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
  await signedRequest(actor, "POST", "/v1/devices/register", {
    deviceId: actor.deviceId,
    userId: actor.userId,
    publicKey: actor.publicKey,
    displayName: actor.displayName
  });
}

const suffix = Date.now().toString(36);
const owner = identity(randomUUID(), "Counter Owner");
const adventurer = identity(randomUUID(), "Counter Adventurer");
const guildId = `counter-guild-${suffix}`;
const inviteCode = `C${suffix.slice(-7)}`.toUpperCase();

await register(owner);
await register(adventurer);
await signedRequest(owner, "POST", "/v1/guilds", {
  guildId,
  name: "Counter flow smoke test",
  inviteCode,
  inviteExpiresAt: Date.now() + 60 * 60 * 1000,
  ownerProfileId: `owner-profile-${suffix}`,
  ownerDisplayName: owner.displayName
}, 201);
const joinRequestId = randomUUID();
await signedRequest(adventurer, "POST", "/v1/guild-join-requests", {
  requestId: joinRequestId,
  inviteCode,
  applicantProfileId: `adventurer-profile-${suffix}`,
  applicantDisplayName: adventurer.displayName,
  requestedSide: "ADVENTURER"
}, 201);
await signedRequest(owner, "POST", `/v1/guild-join-requests/${joinRequestId}/decision`, {
  decision: "APPROVED"
});

const acceptSessionId = `counter-accept-${suffix}`;
await signedRequest(adventurer, "POST", "/v1/counter-sessions", {
  sessionId: acceptSessionId,
  guildId,
  action: "ACCEPT_QUEST",
  nonceHash: createHash("sha256").update(randomBytes(32)).digest("base64url"),
  encryptedSummary: JSON.stringify({
    adventurerProfileId: `adventurer-profile-${suffix}`,
    adventurerName: adventurer.displayName,
    questId: "quest-smoke",
    questTitle: "Smoke Quest"
  }),
  expiresAt: Date.now() + 10 * 60 * 1000
}, 201);
const managerView = await signedRequest(owner, "GET", `/v1/counter-sessions?guildId=${guildId}`);
if (!managerView.sessions.some((session) => session.sessionId === acceptSessionId)) {
  throw new Error("Manager could not see the adventurer counter request.");
}
await signedRequest(adventurer, "POST", `/v1/counter-sessions/${acceptSessionId}/confirm`, {}, 403);
await signedRequest(owner, "POST", `/v1/counter-sessions/${acceptSessionId}/confirm`, {});
const adventurerReceipt = await signedRequest(adventurer, "GET", `/v1/counter-sessions?guildId=${guildId}`);
if (!adventurerReceipt.sessions.some((session) =>
  session.sessionId === acceptSessionId && session.status === "COMPLETED")) {
  throw new Error("Adventurer did not receive the completed acceptance receipt.");
}

const settlementSessionId = `counter-settle-${suffix}`;
await signedRequest(owner, "POST", "/v1/counter-sessions", {
  sessionId: settlementSessionId,
  guildId,
  action: "SETTLE_SUBMISSION",
  adventurerUserId: adventurer.userId,
  nonceHash: createHash("sha256").update(randomBytes(32)).digest("base64url"),
  encryptedSummary: JSON.stringify({
    adventurerProfileId: `adventurer-profile-${suffix}`,
    adventurerName: adventurer.displayName,
    managerProfileId: `owner-profile-${suffix}`,
    managerName: owner.displayName,
    questId: "quest-smoke",
    questTitle: "Smoke Quest",
    submissionId: "submission-smoke",
    approved: true,
    proposedBonusGp: 5,
    proposedBonusExp: 3
  }),
  expiresAt: Date.now() + 10 * 60 * 1000
}, 201);
await signedRequest(owner, "POST", `/v1/counter-sessions/${settlementSessionId}/confirm`, {}, 403);
await signedRequest(adventurer, "POST", `/v1/counter-sessions/${settlementSessionId}/confirm`, {});

console.log(JSON.stringify({
  ok: true,
  guildId,
  acceptanceConfirmedBy: "MANAGER",
  settlementConfirmedBy: "ADVENTURER",
  wrongSideConfirmationsRejected: true
}, null, 2));
