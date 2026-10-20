CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_email_normalized CHECK (
        email = lower(btrim(email)) AND length(email) BETWEEN 3 AND 254
    ),
    CONSTRAINT users_password_hash_not_empty CHECK (length(password_hash) > 0),
    CONSTRAINT users_email_unique UNIQUE (email)
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_token_hash TEXT NOT NULL UNIQUE,
    access_expires_at TIMESTAMPTZ NOT NULL,
    refresh_token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sessions_access_token_hash_not_empty CHECK (length(access_token_hash) > 0),
    CONSTRAINT sessions_refresh_token_hash_not_empty CHECK (length(refresh_token_hash) > 0),
    CONSTRAINT sessions_expiration_valid CHECK (
        access_expires_at > created_at AND access_expires_at <= expires_at
    ),
    CONSTRAINT sessions_revocation_valid CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX sessions_user_id_idx ON sessions(user_id);
CREATE INDEX sessions_expires_at_idx ON sessions(expires_at);