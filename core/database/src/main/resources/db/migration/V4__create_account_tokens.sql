CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE CHECK (length(token_hash) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    used_at TIMESTAMPTZ CHECK (used_at IS NULL OR used_at >= created_at)
);

CREATE INDEX email_verification_tokens_user_id_idx ON email_verification_tokens(user_id);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE CHECK (length(token_hash) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    used_at TIMESTAMPTZ CHECK (used_at IS NULL OR used_at >= created_at)
);

CREATE INDEX password_reset_tokens_user_id_idx ON password_reset_tokens(user_id);