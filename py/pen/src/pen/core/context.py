"""The per-pass handler context (spec §11.1).

Assembled by the scheduler once per pass and handed to ``handler.run(ctx)``. It carries the
engine (the handler opens its own per-row transaction — see note below), the per-pass taxonomy
maps, and the run correlation id + logger.

The LLM provider is **not** here: each handler owns its own provider (built from its
per-handler :class:`~pen.parse_handler_config.LLMSettings`), so different sources can use
different providers/models (spec §5, per-handler providers). The handler passes its provider to
``parser.parse`` itself.

Deviation from the spec's indicative dataclass (§11.1 lists ``db: Connection``): the handler
needs a *fresh transaction per parsed row* (spec §4.3 / §7), and the candidate scan is a
separate read, so the context carries the :class:`~sqlalchemy.engine.Engine` rather than one
long-lived connection. A ``stop_event`` is also carried so the handler can break between rows
and interrupt the 60s rate-limit pause on shutdown.
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass

from sqlalchemy.engine import Engine

from pen.db.categories_repo import CategoryMaps

__all__ = ["ParseContext"]


@dataclass(frozen=True, slots=True)
class ParseContext:
    """Everything a handler needs for one pass over its source's candidates.

    Attributes:
        scan_source_id: The source's surrogate id — written as ``source_id`` on retail rows and
            the ``parse_scan_run_metadata`` key. Never a hardcoded literal.
        source_table: The handler's scan table (e.g. ``swiggy_instamart_scan``); used in the
            candidate anti-join and the ``parse_failures`` provenance.
        engine: Shared DB engine; the handler opens a read for the scan and a ``begin()``
            transaction per parsed row.
        cat_maps: The per-pass taxonomy maps (built fresh each pass from the ``05`` seed).
        run_id: Correlation id for logs across this pass.
        log: Pass-level context-bound logger (run_id + source; user/message rebound per row).
        stop_event: Set on SIGTERM/SIGINT; the handler checks it between rows and the retry
            wrapper waits on it during the rate-limit pause, so shutdown is never mid-write.
    """

    scan_source_id: int
    source_table: str
    engine: Engine
    cat_maps: CategoryMaps
    run_id: str
    log: logging.LoggerAdapter  # type: ignore[type-arg]
    stop_event: threading.Event
