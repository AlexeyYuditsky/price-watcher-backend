CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT sessions_expiration_valid CHECK (expires_at > created_at),
    CONSTRAINT sessions_revocation_valid CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT sessions_refresh_token_hash_not_empty CHECK (length(refresh_token_hash) > 0)
);

CREATE INDEX sessions_user_id_idx ON sessions(user_id);
CREATE INDEX sessions_expires_at_idx ON sessions(expires_at);