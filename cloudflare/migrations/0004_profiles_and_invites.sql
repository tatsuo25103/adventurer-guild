ALTER TABLE accounts
ADD COLUMN display_name TEXT NOT NULL DEFAULT '';

ALTER TABLE guild_invites
ADD COLUMN one_time INTEGER NOT NULL DEFAULT 0;

ALTER TABLE guild_invites
ADD COLUMN used_at INTEGER;

ALTER TABLE guild_invites
ADD COLUMN used_by_user_id TEXT;

CREATE INDEX IF NOT EXISTS idx_guild_invites_active
ON guild_invites(guild_id, one_time, revoked_at, expires_at, used_at);
