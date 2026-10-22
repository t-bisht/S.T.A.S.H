#!/usr/bin/env bash
# run_local.sh — start the STASH parse engine (pen) standalone for local dev.
#
# Secrets/config come from py/pen/.env (gitignored) when present — copy .env.example to .env
# and edit. Anything already exported in your shell wins over .env (so you can override ad hoc,
# e.g. `LOG_LEVEL=DEBUG ./run_local.sh`). The engine logs to the console AND to
# $LOG_DIR/pen.log (rotating). Ctrl-C = clean shutdown (SIGINT -> stop_event).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # py/pen/
REPO_PY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"                # py/

# ── Load .env (gitignored) without clobbering vars already set in the shell ──────────────────
ENV_FILE="${SCRIPT_DIR}/.env"
if [[ -f "${ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    while IFS='=' read -r key value; do
        [[ -z "${key}" || "${key}" == \#* ]] && continue        # skip blanks/comments
        key="${key// /}"
        [[ -n "${!key:-}" ]] && continue                         # shell override wins
        value="${value%$'\r'}"                                   # strip CR if file is CRLF
        export "${key}=${value}"
    done < "${ENV_FILE}"
fi

# ── Defaults for anything still unset (mechanical knobs only; no secrets here) ───────────────
# Provider/model/endpoint per handler are CODE config (parse_handler_config.py), not env.
export LOG_DIR="${LOG_DIR:-logs}"
export LOG_LEVEL="${LOG_LEVEL:-INFO}"

# ── Fail fast on the default handler's key + DB target (clearer than a Python traceback) ─────
# The v1 swiggy handler uses GROQ_API_KEY; add other providers' keys as you enable their handlers.
: "${GROQ_API_KEY:?set GROQ_API_KEY (swiggy handler provider key; secret) in py/pen/.env}"
if [[ -z "${DATABASE_URL:-}" && ( -z "${DB_HOST:-}" || -z "${DB_NAME:-}" || -z "${DB_USER:-}" || -z "${DB_PASSWORD:-}" ) ]]; then
    echo "run_local.sh: set DATABASE_URL, or all of DB_HOST/DB_NAME/DB_USER/DB_PASSWORD, in py/pen/.env" >&2
    exit 2
fi

cd "${REPO_PY_DIR}"
uv sync
exec uv run --package pen python -m pen.main
