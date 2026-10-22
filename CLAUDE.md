# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Stash** (S.T.A.S.H — Spend Tracking Analysis Smart Hub) — personal expense tracker. Scans
bills/receipts from a user's Gmail, strips them to clean text, and (future) parses + categorizes
them into spend. v1 source: **Swiggy Instamart**.

Design specs live in a **sibling** repo, not here: `../arthasetu/stash/...` (referenced in SQL
headers as `$STASH_REPO/../arthasetu/stash/...`). This repo is implementation + infra only.

## Repo layout

Polyglot monorepo. Each top-level dir is an independent stack with its own README — read it first.

| Dir | Stack | What | Port |
|-----|-------|------|------|
| `java/user_engine` | Spring Boot 3.3.4 / Java 21 (Gradle) | Login, sessions, encrypted OAuth token store, internal token broker for engines | 7083 |
| `py/ingestion` | Python 3.12 / uv / SQLAlchemy | Long-running scheduled Gmail scan→ingest worker | none (worker) |
| `web` | React 18 + Vite 6 + TS + Tailwind | Login SPA | 7001 (dev) |
| `infra/self_hosted` | Docker Compose | Postgres + backend + ingestion stack | pg 7432 |

`py/stager` is a planned uv workspace member (listed in `py/pyproject.toml` + `py/README.md`) —
no source yet.

## Architecture

```
browser (web SPA)
   │  Google OAuth → Hiemdall handoff
   ▼
user_engine (7083) ──internal token broker──► ingestion worker
   │  stores encrypted refresh+access tokens        │ scans Gmail
   ▼                                                 ▼
Hiemdall (9082, external)                    stash Postgres (shared)
   exchanges codes, refreshes Google tokens
```

- **Auth is delegated to Hiemdall** (external service at `9082`, its own compose stack). The SPA
  does a Google OAuth handoff; `user_engine` redeems it via Hiemdall (`POST /auth/session`), mints
  an **opaque server-side session cookie** (not a JWT yet). user_engine also calls Hiemdall to
  refresh dead Google access tokens.
- **user_engine is the sole holder of OAuth tokens**, encrypted at rest (AES-GCM, key
  `STASH_TOKEN_ENC_KEY`). Engines never touch `user_oauth_accounts`; they fetch a plaintext Google
  access token from the internal broker endpoints (`/internal/**`, guarded by `X-Internal-Auth:
  $INTERNAL_SVC_TOKEN`). See `java/user_engine/README.md` for the full contract + metrics.
- **ingestion** is source-agnostic framework + pluggable source handlers (v1 `swiggy_instamart`).
  It scans, strips to text, and persists rows with `has_parsed=false` — it does **not** parse. A
  new source costs a handler, not a framework change.

## Database

Single shared `stash` Postgres DB. Schema is raw SQL in `infra/self_hosted/pg-init/`, run in
alpha order on an **empty** volume only (existing volumes apply by hand — files are idempotent):

| File | Owner | Tables |
|------|-------|--------|
| `02_users.sql` | user_engine | `users`, `user_oauth_accounts`, `sessions` |
| `03_token_refresh.sql` | user_engine | adds refresh-bookkeeping columns to `user_oauth_accounts` |
| `04_ingestion_scan.sql` | ingestion | `scan_sources`, `user_scan_preferences`, `source_scan_run_metadata`, `swiggy_instamart_scan` |
| `05_item_categories.sql` | parse module (future) | `item_categories`, `item_subcategories` (two-level LLM taxonomy) |

User FK column is **`user_id`** referencing `users(user_id)` everywhere (a spec that said `uuid`
was corrected against the real schema — see the SQL headers).

## Build / test

```bash
# Java
cd java && ./gradlew build                     # all subprojects
./gradlew :user_engine:bootRun                 # run user_engine
./gradlew :user_engine:test
./gradlew format                               # Spotless

# Python
cd py && uv sync
uv run --package ingestion pytest
uv run --package ingestion python -m ingestion.main

# Web
cd web && npm install
npm run dev                                    # vite, :7001
npm run test                                   # vitest
npm run lint

# Infra (full local stack)
cd infra/self_hosted && cp .env.example .env   # fill secrets
docker compose up -d
```

## Conventions

- Credentials (`g_credentials.md`, `gcloud_app_credentials.json`, `stash_web_token.json`, `.env`,
  `infra/secrets/`) are gitignored — never commit them or their contents.
- `uv.lock` **is** committed; `.venv/`, `node_modules/`, Gradle `build/`, `dist/` are not.
- Java: group `org.tb.stash`, feature packages split `controller/service/crud/domain/exception`,
  shared entities in `core/`.

## Port convention

All Stash-owned service ports — host **and** container-internal — live in the **7xxx range
(7000–7999)**. 8xxx is reserved for other apps on the host.

- Pick 7xxx for `server.port`, `EXPOSE`, Dockerfile healthchecks, compose `ports:` mappings, docs.
- Compose mappings are symmetric (e.g. `7083:7083`), not 8xxx→7xxx.
- External integrations keep their own ports (Hiemdall `9082`) — the rule is Stash-only.
- Known assignments:
  - PostgreSQL: `7432` (host) → `5432` (container, Postgres default stays)
  - `user_engine`: `7083`
  - `web` dev server: `7001`
