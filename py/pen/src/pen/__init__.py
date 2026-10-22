"""pen — the STASH content (ledger) parse engine.

A scheduled, long-running worker that reads *unparsed* expense mails persisted by the
ingestion engine, sends each body to a configurable (OpenAI-compatible) LLM to extract a
strict JSON schema, maps items onto the two-level item taxonomy, and writes structured spend
into the ``online_retail_*`` tables — one transaction per parsed order.

See ``$STASH_REPO/../arthasetu/stash/design/content_parsing/content_parsing_spec.md``.
"""

from __future__ import annotations

__all__ = ["__version__"]

__version__ = "0.1.0"
