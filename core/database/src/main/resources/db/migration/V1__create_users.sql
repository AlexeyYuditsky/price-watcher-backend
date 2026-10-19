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