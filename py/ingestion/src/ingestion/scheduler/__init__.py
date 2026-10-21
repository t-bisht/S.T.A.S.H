"""The source-agnostic scheduling framework.

- :mod:`ingestion.scheduler.due`    — the pure "is this user due?" predicate.
- :mod:`ingestion.scheduler.runner` — one source pass: iterate enrolled users, each in
  isolation, resolving the watermark, running the handler, and writing run metadata.
- :mod:`ingestion.scheduler.loop`   — the long-running poll loop that reads ``scan_sources``
  fresh each tick and dispatches due work to the runner.
"""

from __future__ import annotations

__all__: list[str] = []
