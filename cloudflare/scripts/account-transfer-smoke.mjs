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

async function register(actor, transferCode = "", expectedStatus = 200) {
  return signedRequest(actor, "POST", "/v1/devices/register", {
    deviceId: actor.deviceId,
    userId: actor.userId,
    publicKey: actor.publicKey,
    displayName: actor.displayName,
    transferCode
  }, expectedStatus);
}

const userId = randomUUID();
const oldPhone = identity(userId, "Old phone");
const newPhone = identity(userId, "New phone");
const attackerPhone = identity(userId, "Attacker phone");

await register(oldPhone);
await register(newPhone, "", 401);
const transfer = await signedRequest(oldPhone, "POST", "/v1/me/account-transfer", {}, 201);
const inherited = await register(newPhone, transfer.transferCode);
if (!inherited.inherited || inherited.userId !== userId) {
  throw new Error(`New phone did not inherit the account: ${JSON.stringify(inherited)}`);
}
await register(attackerPhone, transfer.transferCode, 401);
const devices = await signedRequest(newPhone, "GET", "/v1/me/devices");
if (devices.devices.length !== 2) {
  throw new Error(`Expected two active devices: ${JSON.stringify(devices)}`);
}
await signedRequest(newPhone, "POST", `/v1/me/devices/${encodeURIComponent(oldPhone.deviceId)}/revoke`, {});
await signedRequest(oldPhone, "GET", "/v1/me/devices", null, 401);

console.log(JSON.stringify({
  ok: true,
  userId,
  inheritedOnNewDevice: true,
  transferCodeWasOneTime: true,
  oldDeviceRevoked: true
}, null, 2));
