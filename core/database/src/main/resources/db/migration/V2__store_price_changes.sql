-- Price history keeps only price changes; the time of the latest check lives in products.
ALTER TABLE products ADD COLUMN last_checked_at TIMESTAMPTZ;
-- Pieces in a pack; the price per piece is derived from it when prices are read.
ALTER TABLE products ADD COLUMN units_per_pack INTEGER CHECK (units_per_pack > 1);

UPDATE products p
SET last_checked_at = c.last_checked_at
FROM (
    SELECT product_id, max(checked_at) AS last_checked_at
    FROM price_checks
    GROUP BY product_id
) c
WHERE c.product_id = p.id;

-- A product nobody tracks is removed together with its history.
DELETE FROM products p
WHERE NOT EXISTS (SELECT 1 FROM tracked_products t WHERE t.product_id = p.id);

DELETE FROM price_checks c
USING (
    SELECT id,
           amount = lag(amount) OVER w AND currency = lag(currency) OVER w AS unchanged
    FROM price_checks
    WINDOW w AS (PARTITION BY product_id ORDER BY checked_at, id)
) d
WHERE c.id = d.id AND d.unchanged;

-- Changes are always read by product and time, so that pair becomes the key instead of a surrogate id.
ALTER TABLE price_checks
    DROP COLUMN id,
    DROP COLUMN regular_amount,
    DROP COLUMN bank_amount,
    DROP COLUMN availability;
DROP INDEX price_checks_product_id_checked_at_idx;
ALTER TABLE price_checks RENAME TO price_changes;
ALTER TABLE price_changes RENAME COLUMN checked_at TO changed_at;
ALTER TABLE price_changes RENAME CONSTRAINT price_checks_product_id_fkey TO price_changes_product_id_fkey;
ALTER TABLE price_changes RENAME CONSTRAINT price_checks_amount_check TO price_changes_amount_check;
ALTER TABLE price_changes ADD CONSTRAINT price_changes_pkey PRIMARY KEY (product_id, changed_at);