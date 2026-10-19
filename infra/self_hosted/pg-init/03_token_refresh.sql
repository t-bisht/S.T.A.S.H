-- 03_token_refresh.sql — Google access-token refresh bookkeeping for user_engine.
-- See $STASH_REPO/../arthasetu/stash/features/google_access_token/01_token_refresh.md §6.
--
-- Runs automatically only on an EMPTY Postgres volume (docker-entrypoint-initdb.d).
-- Existing volumes must apply it by hand (idempotent):
--   psql -h 127.0.0.1 -p 7432 -U $PG_USER -d $PG_DATABASE < infra/self_hosted/pg-init/03_token_refresh.sql
--
-- Columns:
--   login_required     — true once Hiemdall reports reauth_required (refresh token dead).
--                        Sweep and on-demand refresh skip the row; re-login clears it.
--   last_refreshed_at  — wall-clock of the last successful refresh; drives on-demand coalescing.
--   last_refresh_error — stable error code of the last failed attempt (never token data).

ALTER TABLE user_oauth_accounts
  ADD COLUMN IF NOT EXISTS login_required     BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS last_refreshed_at  TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS last_refresh_error TEXT;

-- Serves the sweep's keyset-paged "due for refresh" query.
CREATE INDEX IF NOT EXISTS idx_oauth_refresh_due
  ON user_oauth_accounts (access_token_expires_at, oauth_account_id)
  WHERE login_required = false;
