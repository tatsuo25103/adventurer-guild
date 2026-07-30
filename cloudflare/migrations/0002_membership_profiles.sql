ALTER TABLE guild_memberships
ADD COLUMN profile_id TEXT NOT NULL DEFAULT '';

ALTER TABLE guild_memberships
ADD COLUMN display_name TEXT NOT NULL DEFAULT '';
