-- 02_users.sql — Phase 2 identity schema for user_engine.
-- See $STASH_REPO/../arthasetu/stash/design/login_and_google_auth/stash_google_auth_spec.adoc §4.3.
--
-- Three tables:
--   users               — one row per human; STASH-local UUID identity.
--   user_oauth_accounts — linked OAuth providers (v1: google only).
--   sessions            — opaque server-side session handles. Temporary layer
--                         until Hiemdall-issued JWT sessions ship.

-- gen_random_uuid() ships in core since Postgres 13; pgcrypto left here for
-- explicitness and compatibility with older minor versions.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ─── users ──────────────────────────────────────────────────────────────────
CREATE TABLE users (
    user_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    primary_email    TEXT NOT NULL,
    display_name     TEXT,
    picture_url      TEXT,
    status           TEXT NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE','DISABLED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at    TIMESTAMPTZ
);

CREATE INDEX idx_users_primary_email ON users (primary_email);

-- ─── user_oauth_accounts ────────────────────────────────────────────────────
-- Carries identity claims (sub, email), the encrypted long-lived credential
-- (refresh_token) and the encrypted short-lived access_token + its wall-clock
-- expiry. id_token is NOT stored (short-lived; claims denormalized here).
CREATE TABLE user_oauth_accounts (
    oauth_account_id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID        NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    provider                 TEXT        NOT NULL,
    provider_sub             TEXT        NOT NULL,
    provider_email           TEXT,
    scope                    TEXT        NOT NULL,
    refresh_token_enc        BYTEA       NOT NULL,
    refresh_token_nonce      BYTEA       NOT NULL,
    access_token_enc         BYTEA       NOT NULL,
    access_token_nonce       BYTEA       NOT NULL,
    access_token_expires_at  TIMESTAMPTZ NOT NULL,
    linked_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_sub)
);

CREATE INDEX idx_user_oauth_accounts_user ON user_oauth_accounts (user_id);

-- ─── sessions ───────────────────────────────────────────────────────────────
-- Opaque server-side sessions. Hot path: read on every authenticated request.
-- Temporary — swap for Hiemdall JWT verification when that feature lands.
CREATE TABLE sessions (
    session_handle   TEXT PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    issued_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_sessions_user    ON sessions (user_id);
CREATE INDEX idx_sessions_expires ON sessions (expires_at);
