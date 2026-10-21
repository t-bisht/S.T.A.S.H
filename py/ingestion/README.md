# ingestion — STASH Gmail scan & ingestion engine

A scheduled, long-running engine that scans enrolled users' Gmail for expense-bearing mails,
strips each mail to clean text, and persists it for a downstream LLM parse module. v1 ships a
source-agnostic scan framework plus one pluggable source handler: **Swiggy Instamart**.

Ingestion stops at writing rows with `has_parsed = false`. It does **not** parse, and it does
not decide how parse is triggered — that is a separate spec.

## Architecture

Two layers, deliberately separated so a new source costs a handler, not a framework change:

| Layer | Responsibility |
|-------|----------------|
| **Framework** (source-agnostic) | Scheduler poll loop, per-user isolation, token broker, watermark/metadata, logging. |
| **Source handlers** (pluggable) | One per source: the Gmail query + the mail→row mapping. v1: `swiggy_instamart`. |

Dispatch: a `scan_sources` row's `scan_source_name` is looked up in `HANDLERS`
(`sources/registry.py`); DB writes are FK'd by the row's surrogate `scan_source_id`. A row
whose name has no registered handler is logged and skipped — never a crash.

### Package layout

```
src/ingestion/
├── main.py            entrypoint: config → logging → engine/broker/gmail → loop
├── config.py          env bootstrap (DB DSN, user_engine URL, INTERNAL_SVC_TOKEN, logging)
├── logging_setup.py   dual console + rotating-file logging; per-run correlation context
├── scheduler/         due.py (pure predicate), runner.py (per-user isolation), loop.py (poll)
├── tokens/            broker.py (user_engine client + with_gmail_token), exceptions.py
├── gmail/             client.py — reusable search / fetch / decode / html_to_text
├── sources/           base.py (handler ABC + generic id-diff), registry.py, swiggy_instamart/
└── db/                engine.py, models.py (Core tables), one *_repo.py per table
```

## Data flow (per user, Swiggy Instamart)

1. **Watermark** — read `source_scan_run_metadata`; `since = last_scan_time`, else
   `now - scan_init_range_seconds` on a first run.
2. **Token** — `broker.get_access_token(user_id)` (no speculative refresh).
3. **Query** — `subject:"your instamart order" after:<epoch(since - skew)>`.
4. **Search** — fully paginated; accumulate *all* matching ids (500/page, no truncation).
5. **id-diff** — ask the DB which ids already exist; fetch full bodies only for the new ones.
6. **Fetch + strip** — `format=full`, decode body, `html_to_text` → clean text.
7. **Persist** — `INSERT ... ON CONFLICT (user_id, message_id) DO NOTHING`, `has_parsed=false`.
8. **Advance watermark** — on success only, upsert metadata `SUCCESS` with the run start time.

Three-layer dedup (tight `after:` epoch → id-diff → unique constraint) makes reruns safe at
any cadence. A failed run leaves the watermark untouched, so the next run retries the window.

## Configuration

DB is the single source of truth for scan *behaviour*; env holds only connection + secrets.
See [`.env.example`](.env.example). Required: `USER_ENGINE_BASE_URL`, `INTERNAL_SVC_TOKEN`, and
either `DATABASE_URL` or the `DB_*` parts.

## Run

```bash
# From the py/ workspace (requires all workspace members present):
cd py
uv sync
uv run --package ingestion python -m ingestion.main

# Tests (resolve the package from src/ without an editable install):
uv run --package ingestion pytest        # or: cd py/ingestion && pytest

# Container (builds standalone from this dir's pyproject — no workspace needed):
docker build -t stash-ingestion py/ingestion
docker run --rm --env-file py/ingestion/.env -v "$PWD/logs:/logs" stash-ingestion
```

## Database

Shares the one `stash` Postgres DB with `user_engine`. The four tables
(`scan_sources`, `user_scan_preferences`, `source_scan_run_metadata`, `swiggy_instamart_scan`)
are created by the raw migration `infra/self_hosted/pg-init/04_ingestion_scan.sql`, which also
seeds the `swiggy_instamart` source row.

> **Schema note:** the spec's DDL named the user FK column `uuid` referencing `users(uuid)`.
> The real `users` table (02_users.sql) has PK `user_id`, and every sibling table uses
> `user_id` referencing `users(user_id)`. This engine follows that convention: the FK column is
> `user_id`. (Spec §0 authorises correcting the FK target against the real schema.)

## Adding a new source

Two coordinated changes (spec §2):

1. **Code** — write a `SourceHandler` and register it in `HANDLERS` under its `source_name`.
2. **DB** — insert a `scan_sources` row (name == registry key) and enroll users in
   `user_scan_preferences`.

Tuning or enrolling an *existing* source is DB-only (no deploy). The reusable `GmailClient` and
the generic id-diff routine are reused untouched; a handler writes no Gmail/HTTP code.
