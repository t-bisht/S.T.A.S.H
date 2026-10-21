-- 04_ingestion_scan.sql — Gmail scan & ingestion engine schema (py/ingestion).
-- See $STASH_REPO/../arthasetu/stash/design/instamart_order_scan_and_parsing/ingestion_spec.md §9.
--
-- Runs automatically only on an EMPTY Postgres volume (docker-entrypoint-initdb.d),
-- in alphabetical order after 02_users.sql / 03_token_refresh.sql.
-- Existing volumes must apply it by hand (idempotent):
--   psql -h 127.0.0.1 -p 7432 -U $PG_USER -d $PG_DATABASE < infra/self_hosted/pg-init/04_ingestion_scan.sql
--
-- NOTE on naming (deviation from spec §9, authorised by spec §0):
--   The spec DDL names the user FK column `uuid` and references `users(uuid)`. The real
--   STASH users table (02_users.sql) has PK column `user_id`, and every sibling table
--   (user_oauth_accounts, sessions) uses `user_id` referencing `users(user_id)`. We follow
--   that established convention here: the FK column is `user_id`. Ingestion shares the one
--   `stash` database with user_engine, so the hard FKs to `users(user_id)` are valid.
--
-- Four tables:
--   scan_sources             — hand-seeded, source-agnostic scan config (cadence/lookback).
--   user_scan_preferences    — which users are enrolled per source.
--   source_scan_run_metadata — per-(user, source) watermark + last-attempt health.
--   swiggy_instamart_scan    — ingested Swiggy Instamart mails (source-specific), has_parsed=false.

-- ─── scan_sources ─────────────────────────────────────────────────────────────
-- scan_source_id : surrogate PK, used ONLY as the FK in the three tables below.
--                  Code never hardcodes its value (differs per environment).
-- scan_source_name : stable, hand-assigned registry key matched to a code handler.
CREATE TABLE IF NOT EXISTS scan_sources (
    scan_source_id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scan_source_name        TEXT        NOT NULL UNIQUE,
    description             TEXT,
    scan_init_range_seconds BIGINT      NOT NULL DEFAULT 5184000,  -- first-run lookback (60 days)
    scan_frequency_seconds  BIGINT      NOT NULL,                  -- cadence; due when now-last >= this
    enabled                 BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ─── user_scan_preferences ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_scan_preferences (
    id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        UUID    NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    scan_source_id INT     NOT NULL REFERENCES scan_sources(scan_source_id),
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, scan_source_id)
);

CREATE INDEX IF NOT EXISTS idx_user_scan_prefs_source ON user_scan_preferences (scan_source_id);

-- ─── source_scan_run_metadata ─────────────────────────────────────────────────
-- Watermark + last-attempt health. One row per (user, source), written on EVERY attempt.
-- last_scan_time advances ONLY on success; a failed run leaves it untouched so the next
-- run retries the same window (safe — see spec §7 three-layer dedup). NULL => use init_range.
CREATE TABLE IF NOT EXISTS source_scan_run_metadata (
    user_id          UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    scan_source_id   INT  NOT NULL REFERENCES scan_sources(scan_source_id),
    last_scan_time   TIMESTAMPTZ,
    last_scan_status TEXT NOT NULL CHECK (last_scan_status IN ('SUCCESS','FAILED')),
    last_error       TEXT,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, scan_source_id)
);

-- ─── swiggy_instamart_scan ──────────────────────────────────────────────────
-- One row per ingested Swiggy Instamart mail. No raw body stored — stripped clean text only.
-- order_id is NULL at ingest; the downstream LLM parse module fills it later.
CREATE TABLE IF NOT EXISTS swiggy_instamart_scan (
    id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    message_id     TEXT NOT NULL,                      -- Gmail message id; dedup key
    received_at    TIMESTAMPTZ NOT NULL,               -- from Gmail internalDate (epoch ms → ts)
    scan_time      TIMESTAMPTZ NOT NULL DEFAULT now(), -- when WE ingested it
    mail_body_text TEXT NOT NULL,                      -- HTML-stripped clean text (no raw body stored)
    order_id       TEXT,                               -- NULLABLE; filled by the LLM parse module
    has_parsed     BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, message_id)                        -- dedup; INSERT ... ON CONFLICT DO NOTHING
);

CREATE INDEX IF NOT EXISTS idx_swiggy_scan_has_parsed ON swiggy_instamart_scan (has_parsed);
CREATE INDEX IF NOT EXISTS idx_swiggy_scan_user       ON swiggy_instamart_scan (user_id);

-- ─── seed: the one v1 source ──────────────────────────────────────────────────
-- scan_source_name MUST match the code registry key (sources/registry.py HANDLERS).
-- Idempotent: re-applying the file leaves an existing row untouched.
INSERT INTO scan_sources (scan_source_name, description, scan_init_range_seconds, scan_frequency_seconds)
VALUES (
    'swiggy_instamart',
    'Swiggy Instamart delivered-order mails (subject:"your instamart order")',
    10368000,   -- 60-day first-run lookback
    3600      -- scan daily
)
ON CONFLICT (scan_source_name) DO NOTHING;
