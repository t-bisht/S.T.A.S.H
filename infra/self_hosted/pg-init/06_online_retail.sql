-- 06_online_retail.sql — content (ledger) parse engine "pen" schema (py/pen).
-- See $STASH_REPO/../arthasetu/stash/design/content_parsing/content_parsing_spec.md §10.
--
-- Runs automatically only on an EMPTY Postgres volume (docker-entrypoint-initdb.d),
-- in alphabetical order after 05_item_categories.sql.
-- Existing volumes must apply it by hand (idempotent):
--   psql -h 127.0.0.1 -p 7432 -U $PG_USER -d $PG_DATABASE < infra/self_hosted/pg-init/06_online_retail.sql
--
-- NOTE on naming (same convention as 04/05):
--   The user FK column is `user_id` referencing `users(user_id)` everywhere — the established
--   STASH schema convention. pen shares the one `stash` DB, so the hard FKs are valid.
--
-- pen reuses ingestion's `scan_sources` as the shared source registry — there is NO
-- `parse_sources` table (spec §2.3). `scan_sources.scan_source_id` is the `source_id`
-- provenance FK on both retail tables and the PK of `parse_scan_run_metadata`.
--
-- Four tables:
--   parse_scan_run_metadata       — per-source run-level health (keyed by scan_source_id).
--   parse_failures                — parked rows (the row-level failure surface).
--   online_retail_order_history   — one row per parsed order (parent).
--   online_retail_purchase_history— one row per item (child, FK → order).
--
-- The source writeback (swiggy_instamart_scan.order_id / has_parsed) uses the EXISTING
-- ingestion-owned table (04) — no schema change here (spec §10.5).

-- ─── parse_scan_run_metadata ────────────────────────────────────────────────
-- Run-level health only, ONE row per source (spec §4.5). A pass that parsed some rows and
-- parked others is still SUCCESS — it did its job. FAILED only when the pass could not start
-- (DB down, LLM auth bad, taxonomy load broken — zero rows processed). Row-level failures live
-- in parse_failures, NOT here: the two are separate surfaces.
CREATE TABLE IF NOT EXISTS parse_scan_run_metadata (
    scan_source_id INT         NOT NULL PRIMARY KEY REFERENCES scan_sources(scan_source_id),
    last_run_time  TIMESTAMPTZ,
    last_status    TEXT        NOT NULL CHECK (last_status IN ('SUCCESS','FAILED')),
    last_error     TEXT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─── parse_failures ─────────────────────────────────────────────────────────
-- A failed row is PARKED, not retried forever: recorded here with a reason, then excluded
-- from future candidate queries by the scanner's anti-join (spec §4.2/§4.4). Re-queue is
-- manual for MVP: delete the row and rerun. `reason` taxonomy (spec §4.4):
--   rate_limit | invalid_json | missing_order_id | validation_error | llm_error.
CREATE TABLE IF NOT EXISTS parse_failures (
    id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    source_table  TEXT        NOT NULL,              -- e.g. 'swiggy_instamart_scan'
    source_row_id INT         NOT NULL,              -- swiggy_instamart_scan.id
    message_id    TEXT,                              -- cross-ref back to the mail
    reason        TEXT        NOT NULL,              -- §4.4 taxonomy
    llm_raw       TEXT,                              -- raw model output for triage (invalid JSON, etc.)
    failed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_table, source_row_id)             -- one park per source row
);

CREATE INDEX IF NOT EXISTS idx_parse_failures_reason ON parse_failures (reason);

-- ─── online_retail_order_history ────────────────────────────────────────────
-- One row per parsed order (parent). order_timestamp = the source row's received_at (NOT the
-- LLM). source_id = the parsing source's scan_source_id (provenance). UNIQUE(user_id, order_id)
-- is the idempotency backstop for the one-transaction-per-row persist (spec §7).
CREATE TABLE IF NOT EXISTS online_retail_order_history (
    id                INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    order_id          TEXT        NOT NULL,
    source_id         INT         NOT NULL REFERENCES scan_sources(scan_source_id),
    item_total_bill   NUMERIC(12,2),
    handling_fee      NUMERIC(12,2),
    delivery_fee      NUMERIC(12,2),
    order_grand_total NUMERIC(12,2),
    order_location    TEXT,
    order_timestamp   TIMESTAMPTZ NOT NULL,          -- = swiggy_instamart_scan.received_at
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, order_id)                        -- idempotency backstop (§7)
);

CREATE INDEX IF NOT EXISTS idx_order_hist_user   ON online_retail_order_history (user_id);
CREATE INDEX IF NOT EXISTS idx_order_hist_source ON online_retail_order_history (source_id);

-- ─── online_retail_purchase_history ─────────────────────────────────────────
-- One row per item (child). category_id / sub_category_id are FROZEN at parse time (spec §9):
-- stored rows keep the IDs they got, no backfill. The composite FK (user_id, order_id) ties
-- each item to its parent order (both inserted in the same transaction, spec §7).
CREATE TABLE IF NOT EXISTS online_retail_purchase_history (
    id              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    order_id        TEXT        NOT NULL,
    source_id       INT         NOT NULL REFERENCES scan_sources(scan_source_id),
    category_id     INT         NOT NULL REFERENCES item_categories(category_id),
    sub_category_id INT         NOT NULL REFERENCES item_subcategories(subcategory_id),
    item_name       TEXT        NOT NULL,
    quantity        NUMERIC(10,2),                    -- prototype extracts it
    item_price      NUMERIC(12,2),                    -- LLM total_price (per line)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (user_id, order_id)
        REFERENCES online_retail_order_history (user_id, order_id)
);

CREATE INDEX IF NOT EXISTS idx_purchase_hist_user  ON online_retail_purchase_history (user_id);
CREATE INDEX IF NOT EXISTS idx_purchase_hist_order ON online_retail_purchase_history (user_id, order_id);
