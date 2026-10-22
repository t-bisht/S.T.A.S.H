-- 05_item_categories.sql — two-level item taxonomy for the LLM parse/categorize module.
-- See $STASH_REPO/../arthasetu/stash/design/content_parsing/content_parsing_design.md.
--
-- Runs automatically only on an EMPTY Postgres volume (docker-entrypoint-initdb.d),
-- in alphabetical order after 04_ingestion_scan.sql.
-- Existing volumes must apply it by hand (idempotent):
--   psql -h 127.0.0.1 -p 7432 -U $PG_USER -d $PG_DATABASE < infra/self_hosted/pg-init/05_item_categories.sql
--
-- Design (two-level taxonomy):
--   item_categories     — ~10 budget-level MAIN categories (the rollup used for spend totals).
--   item_subcategories  — Swiggy Instamart's own leaf categories, each under exactly one main.
--
-- The LLM emits only a SUBCATEGORY leaf (constrained to the seeded names). The main category is
-- never emitted by the model — it is DERIVED by FK: subcategory_name -> category_id. That is why
-- subcategory_name is GLOBALLY UNIQUE: a leaf maps to exactly one main (sub -> main is a function),
-- so the rollup can never drift or contradict the leaf.

-- ─── item_categories ──────────────────────────────────────────────────────────
-- category_id   : surrogate PK, used ONLY as the FK in item_subcategories.
--                 Code never hardcodes its value (IDENTITY differs per environment).
-- category_name : stable, hand-assigned budget-level bucket name.
CREATE TABLE IF NOT EXISTS item_categories (
    category_id   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_name TEXT        NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─── item_subcategories ─────────────────────────────────────────────────────
-- subcategory_name is the value the LLM must pick from. Globally UNIQUE so the
-- parse module can resolve sub -> main with a single lookup.
CREATE TABLE IF NOT EXISTS item_subcategories (
    subcategory_id   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id      INT         NOT NULL REFERENCES item_categories(category_id),
    subcategory_name TEXT        NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_item_subcategories_category ON item_subcategories (category_id);

-- ─── seed: main categories ─────────────────────────────────────────────────────
-- Idempotent: re-applying the file leaves existing rows untouched.
INSERT INTO item_categories (category_name)
VALUES
    ('Groceries'),
    ('Snacks & Sweets'),
    ('Beverages'),
    ('Meat & Seafood'),
    ('Packaged & Frozen'),
    ('Personal Care'),
    ('Health'),
    ('Household'),
    ('Baby & Pet'),
    ('Other')
ON CONFLICT (category_name) DO NOTHING;

-- ─── seed: subcategories ───────────────────────────────────────────────────────
-- FK resolved by name (IDENTITY ids are env-specific, never hardcoded). One INSERT
-- per main: CROSS JOIN the leaf list against its parent row, idempotent on conflict.

INSERT INTO item_subcategories (category_id, subcategory_name)
SELECT c.category_id, s.name
FROM item_categories c
CROSS JOIN (VALUES
    ('Fresh Fruits'),
    ('Fresh Vegetables'),
    ('Dairy, Bread & Eggs'),
    ('Cooking Essentials'),
    ('Cereals & Breakfast'),
    ('Dry Fruits & Seeds')
) AS s(name)
WHERE c.category_name = 'Groceries'
ON CONFLICT (subcategory_name) DO NOTHING;

INSERT INTO item_subcategories (category_id, subcategory_name)
SELECT c.category_id, s.name
FROM item_categories c
CROSS JOIN (VALUES
    ('Chips & Namkeens'),
    ('Biscuits & Cakes'),
    ('Chocolates')
) AS s(name)
WHERE c.category_name = 'Snacks & Sweets'
ON CONFLICT (subcategory_name) DO NOTHING;

INSERT INTO item_subcategories (category_id, subcategory_name)
SELECT c.category_id, s.name
FROM item_categories c
CROSS JOIN (VALUES
    ('Cold Drinks & Juices')
) AS s(name)
WHERE c.category_name = 'Beverages'
ON CONFLICT (subcategory_name) DO NOTHING;

INSERT INTO item_subcategories (category_id, subcategory_name)
SELECT c.category_id, s.name
FROM item_categories c
CROSS JOIN (VALUES
    ('Meat & Seafood')
) AS s(name)
WHERE c.category_name = 'Meat & Seafood'
ON CONFLICT (subcategory_name) DO NOTHING;

INSERT INTO item_subcategories (category_id, subcategory_name)
SELECT c.category_id, s.name
FROM item_categories c
CROSS JOIN (VALUES
    ('Instant & Frozen Foods'),
    ('Bakery')
) AS s(name)
WHERE c.category_name = 'Packaged & Frozen'
ON CONFLICT (subcategory_name) DO NOTHING;

INSERT INTO item_subcategories (category_id, subcategory_name)
SELECT c.category_id, s.name
FROM item_categories c
CROSS JOIN (VALUES
    ('Bath & Body'),
    ('Hair Care'),
    ('Makeup'),
    ('Feminine Hygiene')
) AS s(name)
WHERE c.category_name = 'Personal Care'
ON CONFLICT (subcategory_name) DO NOTHING;

INSERT INTO item_subcategories (category_id, subcategory_name)
SELECT c.category_id, s.name
FROM item_categories c
CROSS JOIN (VALUES
    ('Health & Pharma')
) AS s(name)
WHERE c.category_name = 'Health'
ON CONFLICT (subcategory_name) DO NOTHING;

INSERT INTO item_subcategories (category_id, subcategory_name)
SELECT c.category_id, s.name
FROM item_categories c
CROSS JOIN (VALUES
    ('Cleaners & Repellents'),
    ('Home & Kitchen')
) AS s(name)
WHERE c.category_name = 'Household'
ON CONFLICT (subcategory_name) DO NOTHING;

INSERT INTO item_subcategories (category_id, subcategory_name)
SELECT c.category_id, s.name
FROM item_categories c
CROSS JOIN (VALUES
    ('Baby Care'),
    ('Pet Supplies')
) AS s(name)
WHERE c.category_name = 'Baby & Pet'
ON CONFLICT (subcategory_name) DO NOTHING;

INSERT INTO item_subcategories (category_id, subcategory_name)
SELECT c.category_id, s.name
FROM item_categories c
CROSS JOIN (VALUES
    ('Electronics & Appliances'),
    ('Fashion & Accessories'),
    ('Other')
) AS s(name)
WHERE c.category_name = 'Other'
ON CONFLICT (subcategory_name) DO NOTHING;
