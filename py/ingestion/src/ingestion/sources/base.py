"""Handler contract shared by every source (spec §10).

Defines the data a handler is given (:class:`ScanContext`), what it returns
(:class:`ScanResult`), the handler interface (:class:`SourceHandler`), and the generic
id-diff + fetch-new routine (:func:`fetch_new_messages`) that every Gmail-backed source reuses
— the only source-specific input is the "which of these ids already exist?" lookup.
"""

from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

from sqlalchemy.engine import Connection

from ingestion.gmail.client import GmailClient, ParsedMessage
from ingestion.tokens.broker import TokenBroker, with_gmail_token

__all__ = ["ScanContext", "ScanResult", "SourceHandler", "fetch_new_messages"]


@dataclass(frozen=True, slots=True)
class ScanContext:
    """Everything a handler needs for one user's scan. Assembled by the runner.

    Attributes:
        user_id: The user being scanned.
        scan_source_id: FK for all DB writes (never a hardcoded literal).
        since: Watermark, or the init-range fallback on a first run.
        broker: Reusable token broker.
        db: Live DB connection inside the runner's per-user transaction.
        run_id: Correlation id for logs.
        log: Context-bound logger (carries run_id / user / source on every line).
    """

    user_id: UUID
    scan_source_id: int
    since: datetime
    broker: TokenBroker
    db: Connection
    run_id: str
    log: logging.LoggerAdapter  # type: ignore[type-arg]


@dataclass(frozen=True, slots=True)
class ScanResult:
    """Per-user scan outcome, fed into the runner's summary line.

    Attributes:
        matched: Ids returned by the Gmail search.
        fetched: New ids actually fetched (post id-diff).
        stored: Rows inserted (post ``ON CONFLICT``).
    """

    matched: int
    fetched: int
    stored: int


class SourceHandler(ABC):
    """Base class for a source handler.

    A concrete handler sets :attr:`source_name` to its ``scan_sources.scan_source_name`` (the
    registry key) and implements :meth:`scan`.
    """

    source_name: str

    @abstractmethod
    def scan(self, ctx: ScanContext) -> ScanResult:
        """Scan one user for this source and persist any new mails.

        Must be side-effect-safe to retry: the framework may re-run the same window after a
        failure, and the three-layer dedup (spec §7) keeps that harmless.
        """
        raise NotImplementedError


def fetch_new_messages(
    gmail: GmailClient,
    broker: TokenBroker,
    user_id: UUID,
    all_ids: list[str],
    existing_ids: set[str],
    log: logging.LoggerAdapter,  # type: ignore[type-arg]
) -> list[ParsedMessage]:
    """Fetch full bodies for only the ids not already stored (the id-diff cost saver).

    Generic across sources: the one source-specific input is ``existing_ids`` (the result of
    the handler's "which of these already exist?" lookup against its own table). An overlapping
    re-run whose ids are all already stored fetches zero bodies.

    Each fetch is wrapped in :func:`~ingestion.tokens.broker.with_gmail_token` so a mid-run
    token expiry is refreshed once and retried transparently.

    Args:
        gmail: The shared Gmail client.
        broker: The token broker.
        user_id: The user being scanned.
        all_ids: Every id the search returned.
        existing_ids: The subset already present in the source's table.
        log: Context-bound logger.

    Returns:
        Parsed messages for the new ids, in the order of ``all_ids``.
    """
    new_ids = [message_id for message_id in all_ids if message_id not in existing_ids]
    log.info(
        "id-diff: matched=%d already_stored=%d new=%d",
        len(all_ids),
        len(existing_ids),
        len(new_ids),
    )

    messages: list[ParsedMessage] = []
    for message_id in new_ids:
        fetch_one: Callable[[str], ParsedMessage] = (
            lambda token, _id=message_id: gmail.fetch_message(token, _id)
        )
        messages.append(with_gmail_token(broker, user_id, fetch_one))
    return messages
