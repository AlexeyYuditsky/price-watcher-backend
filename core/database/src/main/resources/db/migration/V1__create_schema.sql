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

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    marketplace TEXT NOT NULL,
    external_id TEXT NOT NULL,
    url TEXT NOT NULL,
    title TEXT,
    image_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_check_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT products_marketplace_external_id_unique UNIQUE (marketplace, external_id),
    CONSTRAINT products_url_not_empty CHECK (length(url) > 0)
);

CREATE INDEX products_next_check_at_idx ON products(next_check_at);

CREATE TABLE tracked_products (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, product_id)
);

CREATE INDEX tracked_products_product_id_idx ON tracked_products(product_id);

CREATE TABLE price_checks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    checked_at TIMESTAMPTZ NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    regular_amount NUMERIC(12, 2) NOT NULL CHECK (regular_amount >= 0),
    bank_amount NUMERIC(12, 2) CHECK (bank_amount IS NULL OR bank_amount >= 0),
    currency TEXT NOT NULL,
    availability TEXT
);

CREATE INDEX price_checks_product_id_checked_at_idx ON price_checks(product_id, checked_at DESC);