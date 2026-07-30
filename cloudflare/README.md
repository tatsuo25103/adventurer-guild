# Adventurer Guild Cloud Relay

Cloudflare is a minimal text relay and authorization service, not a media
archive. The Android app remains offline-first.

It stores:

- device public keys and account/device bindings;
- guild membership, role, invite, and join-request records;
- the guild quest catalog needed by members;
- short-lived counter-session metadata and text summaries;
- replay-prevention nonces and request timestamps.

It does not store task photos, videos, local media URIs, or Nearby payloads.
Text quest details and text proof notes may be stored in D1. Do not put
unnecessary personal or sensitive information in them.

Every non-health request is signed by the device's ECDSA key. The Worker checks
the signature, timestamp, one-time nonce, device/account binding, guild
membership, and operation-specific authorization. Request signing protects
integrity and authentication; it is not end-to-end encryption of D1 content.

The legacy database field name `encrypted_summary` contains a signed,
short-lived counter summary. Its name does not imply application-layer
encryption.

## Setup

1. Create a Cloudflare D1 database named `adventurer-guild-db`.
2. Copy `wrangler.example.jsonc` to `wrangler.jsonc`.
3. Replace `REPLACE_WITH_D1_DATABASE_ID` only in the ignored
   `wrangler.jsonc`.
4. Install dependencies with `npm install`.
5. Apply the schema with `npm run db:remote`.
6. Authenticate with `npx wrangler login`.
7. Deploy with `npm run deploy`.

The first production endpoint to verify is:

```text
GET /health
```

Never commit `wrangler.jsonc`, local Wrangler state, deployment logs, account
identifiers, or production endpoint values. Keep production configuration in
local ignored files or deployment secrets.
