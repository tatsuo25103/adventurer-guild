CREATE TABLE IF NOT EXISTS accounts (
    user_id TEXT PRIMARY KEY,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL
);

INSERT OR IGNORE INTO accounts (user_id, created_at, last_seen_at)
SELECT user_id, MIN(created_at), MAX(last_seen_at)
FROM devices
GROUP BY user_id;

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
