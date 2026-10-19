ALTER TABLE sessions
    ADD COLUMN access_token_hash TEXT UNIQUE,
    ADD COLUMN access_expires_at TIMESTAMPTZ;

ALTER TABLE sessions ADD CONSTRAINT sessions_access_token_pair CHECK (
    (access_token_hash IS NULL AND access_expires_at IS NULL)
    OR (access_token_hash IS NOT NULL AND access_expires_at IS NOT NULL
        AND access_expires_at > created_at AND access_expires_at <= expires_at)
);