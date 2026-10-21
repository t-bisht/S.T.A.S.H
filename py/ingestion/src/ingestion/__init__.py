"""STASH Gmail scan & ingestion engine.

A scheduled, long-running engine that scans enrolled users' Gmail for expense-bearing
mails (v1: Swiggy Instamart delivered-order mails), strips each mail to clean text, and
persists it for a downstream LLM parse module. See the ingestion spec for the full design.

The package is split by responsibility:

- ``config``        — environment bootstrap (DB DSN, token-broker URL, secrets, log config).
- ``logging_setup`` — dual console + rotating-file logging; per-run correlation context.
- ``db``            — SQLAlchemy Core table definitions + one repo module per table.
- ``tokens``        — reusable ``user_engine`` token broker + refresh-once-on-401 helper.
- ``gmail``         — reusable, source-agnostic Gmail REST client (search/fetch/decode/strip).
- ``sources``       — pluggable per-source handlers (query + mail→row mapping) and the registry.
- ``scheduler``     — the poll loop (due computation, dispatch) and per-source/per-user runner.
- ``main``          — process entrypoint wiring it all together.
"""

from __future__ import annotations

__all__ = ["__version__"]

__version__ = "0.1.0"
