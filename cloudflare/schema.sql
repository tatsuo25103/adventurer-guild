PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS accounts (
    user_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    public_key TEXT NOT NULL,
    display_name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    revoked_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_devices_user
ON devices(user_id);

CREATE TABLE IF NOT EXISTS account_transfer_codes (
    code_hash TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    created_by_device_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    used_at INTEGER,
    used_by_device_id TEXT,
    FOREIGN KEY (user_id) REFERENCES accounts(user_id)
);

CREATE INDEX IF NOT EXISTS idx_account_transfer_user
ON account_transfer_codes(user_id, expires_at, used_at);

CREATE TABLE IF NOT EXISTS guilds (
    guild_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    owner_user_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    archived_at INTEGER
);

CREATE TABLE IF NOT EXISTS guild_memberships (
    guild_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    profile_id TEXT NOT NULL DEFAULT '',
    display_name TEXT NOT NULL DEFAULT '',
    side TEXT NOT NULL CHECK (side IN ('MANAGER', 'ADVENTURER')),
    role_certificate TEXT NOT NULL,
    joined_at INTEGER NOT NULL,
    revoked_at INTEGER,
    PRIMARY KEY (guild_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_guild_memberships_user
ON guild_memberships(user_id, revoked_at);

CREATE TABLE IF NOT EXISTS guild_invites (
    invite_code TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    created_by_user_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    one_time INTEGER NOT NULL DEFAULT 0,
    used_at INTEGER,
    used_by_user_id TEXT,
    revoked_at INTEGER,
    FOREIGN KEY (guild_id) REFERENCES guilds(guild_id)
);

CREATE INDEX IF NOT EXISTS idx_guild_invites_guild
ON guild_invites(guild_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_guild_invites_active
ON guild_invites(guild_id, one_time, revoked_at, expires_at, used_at);

CREATE TABLE IF NOT EXISTS guild_join_requests (
    request_id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    applicant_user_id TEXT NOT NULL,
    applicant_profile_id TEXT NOT NULL,
    applicant_display_name TEXT NOT NULL,
    requested_side TEXT NOT NULL CHECK (requested_side IN ('MANAGER', 'ADVENTURER')),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    created_at INTEGER NOT NULL,
    reviewed_at INTEGER,
    reviewed_by_user_id TEXT,
    UNIQUE (guild_id, applicant_user_id),
    FOREIGN KEY (guild_id) REFERENCES guilds(guild_id)
);

CREATE INDEX IF NOT EXISTS idx_guild_join_requests_pending
ON guild_join_requests(guild_id, status, created_at);

CREATE TABLE IF NOT EXISTS counter_sessions (
    session_id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    action TEXT NOT NULL,
    adventurer_user_id TEXT NOT NULL,
    manager_user_id TEXT,
    status TEXT NOT NULL,
    nonce_hash TEXT NOT NULL,
    encrypted_summary TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    completed_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_counter_sessions_guild_status
ON counter_sessions(guild_id, status, expires_at);

CREATE TABLE IF NOT EXISTS guild_quest_catalogs (
    guild_id TEXT PRIMARY KEY,
    catalog_json TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    updated_at INTEGER NOT NULL,
    updated_by_user_id TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(guild_id)
);

CREATE TABLE IF NOT EXISTS encrypted_mailbox_events (
    event_id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    recipient_user_id TEXT NOT NULL,
    sender_device_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    ciphertext TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    acknowledged_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_mailbox_recipient
ON encrypted_mailbox_events(recipient_user_id, acknowledged_at, created_at);

CREATE TABLE IF NOT EXISTS replay_nonces (
    device_id TEXT NOT NULL,
    nonce TEXT NOT NULL,
    used_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    PRIMARY KEY (device_id, nonce)
);
