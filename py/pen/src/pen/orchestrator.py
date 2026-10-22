"""The top orchestrator (spec §2.1 tier 1, §4.1).

At boot it reads the enabled ``scan_sources``, resolves each to its handler + mechanical config,
and starts one :class:`~pen.scheduler.scheduler.Scheduler` per source. It then owns process
lifecycle: block until the shared stop event is set (by the signal handlers in ``main``), then
let every scheduler finish its current wait/row and join.

An enabled source with no registered handler is logged and skipped — never a crash (spec §2.3).
"""

from __future__ import annotations

import logging
import threading

from sqlalchemy.engine import Engine

from pen.config import Config, ConfigError
from pen.core.registry import HANDLERS
from pen.db import scan_sources_repo
from pen.db.scan_sources_repo import ScanSource
from pen.llm.factory import build_provider
from pen.parse_handler_config import config_for
from pen.scheduler.scheduler import Scheduler

__all__ = ["Orchestrator"]

log = logging.getLogger(__name__)


class Orchestrator:
    """Boots and supervises one scheduler per enabled source.

    Each source's handler gets its OWN provider, built from that handler's
    :class:`~pen.parse_handler_config.LLMSettings` + the captured secret map — so Swiggy and
    Paytm can run on different providers/models (spec §5, per-handler providers).
    """

    def __init__(self, engine: Engine, config: Config, stop_event: threading.Event) -> None:
        """Create the orchestrator.

        Args:
            engine: Shared DB engine.
            config: Runtime config (supplies the API-key secret map for provider building).
            stop_event: Set on SIGTERM/SIGINT; the orchestrator blocks on it, then joins.
        """
        self._engine = engine
        self._config = config
        self._stop_event = stop_event
        self._schedulers: list[Scheduler] = []

    def run(self) -> None:
        """Start every scheduler, block until shutdown, then join them cleanly."""
        with self._engine.connect() as conn:
            sources = scan_sources_repo.list_enabled(conn)

        if not sources:
            log.warning("no enabled scan sources — nothing to parse; idling until shutdown")

        for source in sources:
            scheduler = self._build_scheduler(source)
            if scheduler is not None:
                self._schedulers.append(scheduler)

        if not self._schedulers:
            log.warning("no schedulers started (no sources had a registered handler)")

        for scheduler in self._schedulers:
            scheduler.start()
        log.info("orchestrator started %d scheduler(s)", len(self._schedulers))

        # Block the main thread until a shutdown is requested; schedulers run on their own.
        self._stop_event.wait()
        log.info("shutdown requested — joining schedulers")
        for scheduler in self._schedulers:
            scheduler.join()
        log.info("all schedulers joined")

    def _build_scheduler(self, source: ScanSource) -> Scheduler | None:
        """Resolve a source's handler + config + provider into a scheduler.

        Returns ``None`` (and logs) for an unregistered source, or for one whose LLM secret is
        missing — a misconfigured source is skipped so the other sources still run (spec §2.3).
        """
        handler_cls = HANDLERS.get(source.scan_source_name)
        if handler_cls is None:
            log.warning(
                "no handler registered for scan_source_name=%r (scan_source_id=%d) — skipping",
                source.scan_source_name,
                source.scan_source_id,
            )
            return None

        config = config_for(source.scan_source_name)
        try:
            # Build THIS handler's own provider from its own settings + the secret map.
            provider = build_provider(config.llm, self._config.api_keys)
        except ConfigError as exc:
            log.error(
                "cannot build LLM provider for scan_source_name=%r — skipping source: %s",
                source.scan_source_name,
                exc,
            )
            return None

        handler = handler_cls(
            provider,
            retries=config.retries,
            rate_limit_pause_seconds=config.rate_limit_pause_seconds,
        )
        return Scheduler(
            engine=self._engine,
            source=source,
            handler=handler,
            config=config,
            stop_event=self._stop_event,
        )
