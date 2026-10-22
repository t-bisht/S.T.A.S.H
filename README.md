# S.T.A.S.H — Spend Tracking Analysis Smart Hub

Personal expense tracker. It scans a user's Gmail for bills and receipts, strips each mail to
clean text, and (ahead) parses and categorizes them into spend. v1 scans **Swiggy Instamart**
order mails; new sources plug in as handlers.

## Components

| Dir | Stack | Role |
|-----|-------|------|
| [`java/user_engine`](java/user_engine/README.md) | Spring Boot 3.3 / Java 21 | Login (Google OAuth via Hiemdall), sessions, encrypted OAuth token store, internal token broker for engines. Port `7083`. |
| [`py/ingestion`](py/ingestion/README.md) | Python 3.12 / uv | Long-running worker: scheduled Gmail scan → ingest. Writes rows `has_parsed=false`. |
| [`web`](web/README.md) | React 18 + Vite + Tailwind | Login SPA. Dev server `7001`. |
| [`infra/self_hosted`](infra/self_hosted/README.md) | Docker Compose | Postgres + user_engine + ingestion stack for local/self-hosted deployment. |

## How it fits together

```
browser (web SPA) ──Google OAuth handoff──► user_engine (7083) ──► Hiemdall (9082, external)
                                                 │ stores encrypted Google tokens
                       internal token broker ◄───┤
                                                 ▼
                   ingestion worker ──scans Gmail──► shared `stash` Postgres (7432)
```

- Authentication is delegated to **Hiemdall** (a separate external service). `user_engine` is the
  only holder of Google OAuth tokens (encrypted at rest) and hands short-lived access tokens to
  backend engines over an internal, token-guarded API.
- **ingestion** scans and persists raw mail text only — parsing/categorizing is a future module.

Design specs live in a sibling repo (`../arthasetu/stash/...`), not here.

## Quick start (full local stack)

```bash
cd infra/self_hosted
cp .env.example .env          # fill in secrets (PG_PASSWORD, INTERNAL_SVC_TOKEN, STASH_TOKEN_ENC_KEY, …)
docker compose up -d
docker compose ps             # wait for stash-postgres "healthy"
```

Per-component build/test/run commands are in each component's README (linked above).

## Conventions

- All Stash-owned ports live in the **7xxx** range (8xxx is reserved for other host apps).
- Credentials and `.env` files are gitignored — never commit secrets.
- Postgres schema is raw, idempotent SQL in `infra/self_hosted/pg-init/`, applied in order on an
  empty volume.
