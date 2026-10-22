# pen — STASH content (ledger) parse engine

A scheduled, long-running worker that turns the **unparsed** expense mails ingestion stored into
**structured spend**. It reads `swiggy_instamart_scan` rows with `has_parsed=false`, sends each
mail body to a configurable (OpenAI-compatible) LLM, extracts a strict JSON schema, maps every
item onto the two-level item taxonomy, and writes the result into the `online_retail_*` tables —
**one transaction per parsed order**.

Spec: `$STASH_REPO/../arthasetu/stash/design/content_parsing/content_parsing_spec.md`.

## Architecture

Three nested orchestration tiers (spec §2.1), built for isolation — a provider swap or a new
source stays a local change:

```
Orchestrator            reads enabled scan_sources; boots one Scheduler per source; owns lifecycle
   └─ Scheduler (×N)    one per handler, fires on its OWN interval (parse_handler_config)
        └─ Handler      sub-orchestrates scan → parse → persist, one row per transaction
             ├─ scanner   select unparsed, unparked candidates (anti-join parse_failures)
             ├─ parser    prompt → shared LLMProvider → validate → post-process
             └─ persister order + item rows + source writeback, ONE tx
```

- **`llm/` is provider-agnostic; each handler owns its own provider.** A handler declares its
  provider + model + endpoint in `parse_handler_config.LLMSettings`, so Swiggy can run on Groq
  while Paytm runs on OpenRouter / NVIDIA / a locally-hosted model — the factory builds one
  provider per handler. Moving a handler between OpenAI-compatible providers is code-config only
  (edit its `LLMSettings` + supply the key); a non-compatible provider is one new subclass + one
  factory branch (spec §5). Secrets stay in env: `LLMSettings.api_key_env` names the key's env
  var; the key itself is never in code.
- **A handler is a drop-in package** (`handlers/<src>/`). Adding Paytm = new package + one
  `core/registry` line + a `scan_sources` row. Nothing shared is edited.

### Package layout

| Path | Role |
|------|------|
| `main.py` | Wire config + DB engine + `build_provider()` + Orchestrator; signal handlers. |
| `config.py` | Env bootstrap (DB DSN, LLM provider/base_url/**key**/model, logging). |
| `parse_handler_config.py` | Code-level knobs per handler: interval, retries (3), 429 pause (60s). |
| `orchestrator.py` / `scheduler/` | Boot a scheduler per enabled source; lifecycle; per-pass metadata. |
| `llm/` | `LLMProvider` ABC + OpenAI-compatible impl + factory + typed errors. |
| `core/` | Handler ABC, `ParseContext`, retry/pause wrapper, registry. |
| `db/` | Shared repos: scan_sources, run metadata, categories, parse_failures, retail sink. |
| `handlers/swiggy/` | Everything Swiggy: scanner, parser, prompt, persister, schema, handler. |

## Failure model (MVP)

Per row, **no batch-stop** (spec §4.3): a transient LLM error retries 3×; a 429 pauses 60s then
retries; exhausted retries or a validation failure **park** the row in `parse_failures` with a
reason and the pass moves on. A row that parses but fails to *persist* (transient DB error) is
**not** parked — it stays `has_parsed=false` and is retried next pass.

| `reason` | meaning | re-queue (manual: delete the row, rerun) |
|----------|---------|------------------------------------------|
| `rate_limit` | 429 survived retries | yes |
| `invalid_json` | output not parseable JSON after fence-strip | prompt/model review |
| `missing_order_id` | `order_id` null/empty/non-numeric | mail inspection |
| `validation_error` | schema / field validation failed | mail inspection |
| `llm_error` | other provider/transport error after retries | case-by-case |

Run-level health is in `parse_scan_run_metadata` (one row per source): a pass is `SUCCESS` even
with parked rows; `FAILED` only if it couldn't start (DB down, taxonomy not seeded, LLM auth bad).

## Configuration

Three tiers (spec §6): **env** = bootstrap + LLM **secrets** (one API key per provider, e.g.
`GROQ_API_KEY`); **code** = cadence/retry knobs **and each handler's provider/model**
(`parse_handler_config.py`); **DB** = the source registry (`scan_sources`). See `.env.example`.
Unlike ingestion, parse cadence is code-level, not a DB column. There is no single global LLM —
each handler picks its own.

## Run

```bash
# Local standalone (loads py/pen/.env; Ctrl-C = clean shutdown):
./run_local.sh

# From the py/ workspace:
cd py && uv sync && uv run --package pen python -m pen.main

# Tests (resolve the package from src/ without an editable install):
cd py/pen && uv run --package pen pytest       # or: pytest (with pythonpath=src)

# Container (builds standalone from this dir's pyproject — no workspace needed):
docker build -t stash-pen py/pen
```

## Database

pen owns `06_online_retail.sql` (`parse_scan_run_metadata`, `parse_failures`,
`online_retail_order_history`, `online_retail_purchase_history`). It **reads** `scan_sources` +
the item taxonomy (`05`) and **reads/writes-back** `swiggy_instamart_scan` (`04`, ingestion-owned
— `order_id` + `has_parsed` on success). Apply on an existing volume by hand (idempotent):

```bash
psql -h 127.0.0.1 -p 7432 -U $PG_USER -d $PG_DATABASE < infra/self_hosted/pg-init/06_online_retail.sql
```

## Adding a new source

1. Create `handlers/<src>/` (scanner, parser, prompt, persister, schema, handler) — mirror Swiggy.
2. Register it in `core/registry.py` `HANDLERS`.
3. Add a `parse_handler_config.HANDLER_CONFIGS` entry with its own `LLMSettings` (provider,
   base_url, model, `api_key_env`) + interval; supply that provider's key env var.
4. Ensure its `scan_sources` row exists + is `enabled`.

No edit to `llm/`, `core/` (beyond the registry line), the orchestrator, or the scheduler.
