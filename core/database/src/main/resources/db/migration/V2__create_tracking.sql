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