# Google Drive Guild Workspace

## Goals

- The guild leader owns all authoritative guild data in their Google Drive.
- Guild managers and adventurers use their own Google accounts.
- No public user can overwrite the authoritative guild state.
- The app syncs on sign-in, entering a guild, manual refresh, and local changes.
- One Google account may belong to several guilds, with a different side in each guild.

## Drive layout

The guild leader creates one private folder per guild:

```text
Adventurer Guild/
  <guild-id>/
    guild_state.json
    managers/
    member_inboxes/
      <member-google-sub>/
    attachments/
      <member-google-sub>/
    audit/
    backups/
```

The public invitation is a separate, read-only file:

```text
guild_invite_<guild-id>.json
```

It contains only:

- schema version
- guild ID and display name
- invite code
- guild leader Google account ID and email
- authoritative state file ID
- expiry or revocation version

It must not contain member lists, GP, EXP, submissions, permissions, or rewards.

## Access control

| Resource | Guild leader | Manager | Adventurer | Invite holder |
| --- | --- | --- | --- | --- |
| Invitation file | owner | reader | reader | reader |
| Authoritative state | owner | reader | reader | none |
| Manager event inbox | owner | writer on own inbox | none | none |
| Adventurer event inbox | owner | none | writer on own inbox | none |
| Member attachments | owner | reader | writer on own folder | none |
| Audit and backups | owner | reader when permitted | none | none |

Managers do not directly overwrite `guild_state.json`. They submit signed event
files to their own inbox, just like adventurers. This prevents two devices from
silently replacing each other's changes and lets the app enforce guild
permissions before applying an action.

Do not use `type=anyone, role=writer` on any file or folder.

## Join flow

1. The leader shares the read-only invitation link or QR code.
2. The applicant downloads the invitation file and verifies the invite code.
3. The applicant creates a join-request file in their own Drive.
4. The app grants the guild leader `reader` access to that request file.
5. The leader's app discovers pending request files created by this app.
6. After approval, the leader creates a private inbox and attachment folder for
   that Google account and grants that account `writer` access only there.
7. The leader grants the member `reader` access to the authoritative state.
8. The next refresh downloads the member's role and current guild state.

Manager and adventurer applications use separate request types. Approval must
reject a request when the same Google account already holds the opposite side
in that guild.

## Event flow

Every mutation is an immutable JSON event:

```json
{
  "schemaVersion": 1,
  "eventId": "uuid",
  "guildId": "guild-id",
  "actorGoogleSub": "google-sub",
  "actorEmail": "member@example.com",
  "deviceId": "installation-id",
  "type": "SUBMIT_QUEST",
  "targetId": "quest-id",
  "baseRevision": 42,
  "createdAtMillis": 0,
  "payload": {},
  "sha256": "canonical-json-sha256"
}
```

The leader processes events in a deterministic order:

1. Reject duplicate `eventId` values.
2. Verify guild, actor, active membership, side, and permission.
3. Validate the operation against the current authoritative state.
4. Apply the operation or record a rejection reason.
5. Increment the guild revision.
6. Write a new authoritative state and append an audit result.
7. Keep recent Drive revisions and periodic backup files.

The `sha256` detects accidental corruption. It is not a security signature;
authorization comes from the Google account that owns the inbox permission and
from the membership recorded in the authoritative state.

## Conflict rules

- Quest submissions and redemption requests are append-only.
- Reviews reference a submission or redemption ID and are idempotent.
- GP and EXP are never accepted as client-provided totals. The leader derives
  them from approved events.
- Quest and reward edits require `baseRevision`. A stale edit becomes a
  conflict for manual review.
- Duplicate events are ignored.
- Removed members immediately lose Drive permissions to their inbox,
  attachments, and guild state.

## Sync policy

- Pull on Google sign-in, app foreground, entering a guild, and manual refresh.
- Push immediately after a local action when online.
- Queue events locally while offline.
- Managers process pending inbox events when entering the management side or
  refreshing.
- Use Drive file metadata (`modifiedTime`, `version`, and `headRevisionId`) to
  avoid downloading unchanged state.
- Do not poll continuously.

## Required Google configuration

- Enable Google Drive API for the Firebase/Google Cloud project.
- Request only `https://www.googleapis.com/auth/drive.file`.
- Do not use `appDataFolder` for shared guild data because it is private to each
  user's app storage and cannot serve as a shared guild workspace.
- Keep the OAuth consent screen and Android SHA fingerprints configured for the
  production signing key.

## Migration

Existing guilds that store one shared JSON file must be migrated by the guild
leader:

1. Download and validate the old snapshot.
2. Create the private workspace and invitation file.
3. Upload only that guild's scoped state.
4. Replace the old link with the new invitation link.
5. Remove the old `anyone/writer` permission.
6. Provision member inboxes during the next approved membership sync.
