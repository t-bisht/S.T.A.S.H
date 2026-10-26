-- 07_admin_catalog.sql — admin module: product catalog (java/admin_engine).
-- See $STASH_REPO/../arthasetu/stash/design/admin_module/backend/database.md §4.
--
-- Runs automatically only on an EMPTY Postgres volume (docker-entrypoint-initdb.d),
-- in alphabetical order after 06_online_retail.sql.
-- Existing volumes must apply it by hand (idempotent):
--   psql -h 127.0.0.1 -p 7432 -U $PG_USER -d $PG_DATABASE < infra/self_hosted/pg-init/07_admin_catalog.sql
--
-- NOTE on naming (same convention as 04/05/06):
--   User FK column is `user_id` referencing `users(user_id)` everywhere — STASH convention.
--   This table has NO user_id: it is a global reference table.
--
-- One table:
--   digital_shopping_mart_catalog — global product catalog (one row per item_name).

-- ─── digital_shopping_mart_catalog ──────────────────────────────────────────
-- One row per distinct product name. The UNIQUE constraint on item_name makes
-- "one decision per product" structural: any two occurrences of the same product resolve to
-- the same row, and therefore the same category + subcategory. No user_id — global reference.
--
-- item_name       : the product name (UNIQUE — one row per product).
-- category_id     : the main category (FK to item_categories).
-- sub_category_id : the leaf subcategory (FK to item_subcategories).
CREATE TABLE IF NOT EXISTS digital_shopping_mart_catalog (
    catalog_id      INT  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_name       TEXT NOT NULL UNIQUE,
    category_id     INT  NOT NULL REFERENCES item_categories(category_id),
    sub_category_id INT  NOT NULL REFERENCES item_subcategories(subcategory_id)
);

CREATE INDEX IF NOT EXISTS idx_catalog_category    ON digital_shopping_mart_catalog (category_id);
CREATE INDEX IF NOT EXISTS idx_catalog_subcategory ON digital_shopping_mart_catalog (sub_category_id);
