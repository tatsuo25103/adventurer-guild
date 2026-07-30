import {
  createHash,
  generateKeyPairSync,
  randomBytes,
  sign
} from "node:crypto";

const baseUrl = process.env.TEST_BASE_URL || "http://127.0.0.1:8787";
const path = "/v1/devices/register";
const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const publicKeyDer = publicKey.export({ type: "spki", format: "der" });
const publicKeyBase64 = publicKeyDer.toString("base64");
const deviceId = createHash("sha256").update(publicKeyDer).digest("base64url");
const body = JSON.stringify({
  deviceId,
  userId: crypto.randomUUID(),
  publicKey: publicKeyBase64,
  displayName: "Signed smoke test"
});
const timestamp = Date.now().toString();
const nonce = randomBytes(24).toString("base64url");
const bodyHash = createHash("sha256").update(body).digest("base64url");
const canonical = ["POST", path, timestamp, nonce, bodyHash].join("\n");
const signature = sign("sha256", Buffer.from(canonical), {
  key: privateKey,
  dsaEncoding: "ieee-p1363"
}).toString("base64");

const response = await fetch(baseUrl + path, {
  method: "POST",
  headers: {
    "content-type": "application/json",
    "x-device-id": deviceId,
    "x-timestamp": timestamp,
    "x-nonce": nonce,
    "x-signature": signature
  },
  body
});
const responseBody = await response.text();
console.log(JSON.stringify({ status: response.status, body: JSON.parse(responseBody) }, null, 2));
if (!response.ok) process.exitCode = 1;
