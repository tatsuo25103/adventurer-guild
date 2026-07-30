CREATE TABLE IF NOT EXISTS guild_quest_catalogs (
    guild_id TEXT PRIMARY KEY,
    catalog_json TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    updated_at INTEGER NOT NULL,
    updated_by_user_id TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(guild_id)
);

