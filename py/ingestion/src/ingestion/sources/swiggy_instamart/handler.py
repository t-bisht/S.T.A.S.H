"""Swiggy Instamart delivered-order mail handler (spec §7).

Owns only the source-specific parts:

1. the Gmail query (subject match, bounded by a tight ``after:`` epoch from the watermark),
2. the id-diff lookup against ``swiggy_instamart_scan`` (reusing the generic fetch-new routine),
3. the mail → ``swiggy_instamart_scan`` row mapping.

All Gmail mechanics (pagination, fetch, decode, strip) and token handling are delegated to the
reusable framework components.
"""

from __future__ import annotations

from datetime import timedelta

from ingestion.db import swiggy_scan_repo
from ingestion.db.swiggy_scan_repo import SwiggyScanRow
from ingestion.gmail.client import GmailClient
from ingestion.sources.base import ScanContext, ScanResult, SourceHandler, fetch_new_messages
from ingestion.tokens.broker import with_gmail_token

__all__ = ["SwiggyInstamartHandler"]

# Gmail matches the subject substring; quoted so the whole phrase must appear.
_SUBJECT_MATCH = "your instamart order"


class SwiggyInstamartHandler(SourceHandler):
    """Scans a user's Gmail for Swiggy Instamart delivered-order mails."""

    source_name = "swiggy_instamart"

    def __init__(self, gmail: GmailClient, *, skew_seconds: int) -> None:
        """Create the handler.

        Args:
            gmail: Shared, source-agnostic Gmail client.
            skew_seconds: Margin subtracted from the watermark for the ``after:`` bound, to
                tolerate clock skew / late-indexed mail (spec §7.3).
        """
        self._gmail = gmail
        self._skew_seconds = skew_seconds

    def scan(self, ctx: ScanContext) -> ScanResult:
        """Search, id-diff, fetch new, map, and persist for one user.

        Returns:
            A :class:`ScanResult` with matched / fetched / stored counts.
        """
        query = self._build_query(ctx)
        ctx.log.info("gmail query: %s", query)

        # (1) Search → all ids, fully paginated. Wrapped for one-shot refresh on a 401.
        all_ids = with_gmail_token(
            ctx.broker,
            ctx.user_id,
            lambda token: self._gmail.search_all_ids(token, query),
        )

        # (2) id-diff against our own table, then (3) fetch full bodies for new ids only.
        existing = swiggy_scan_repo.existing_message_ids(ctx.db, ctx.user_id, all_ids)
        messages = fetch_new_messages(
            self._gmail, ctx.broker, ctx.user_id, all_ids, existing, ctx.log
        )

        # (4) Map each mail → a row and persist idempotently.
        rows = [
            SwiggyScanRow(
                user_id=ctx.user_id,
                message_id=message.message_id,
                received_at=message.received_at,
                mail_body_text=message.body_text,
            )
            for message in messages
        ]
        stored = swiggy_scan_repo.insert_new(ctx.db, rows)

        ctx.log.info(
            "swiggy scan done: matched=%d fetched=%d stored=%d",
            len(all_ids),
            len(messages),
            stored,
        )
        return ScanResult(matched=len(all_ids), fetched=len(messages), stored=stored)

    def _build_query(self, ctx: ScanContext) -> str:
        """Build the Gmail query bounded by a tight ``after:`` epoch from the watermark.

        ``after:`` accepts a Unix epoch (seconds), so the lower bound is the watermark minus a
        small skew margin — shrinking any re-scan overlap from a whole day to a few minutes.
        """
        lower_bound = ctx.since - timedelta(seconds=self._skew_seconds)
        after_epoch = int(lower_bound.timestamp())
        return f'subject:"{_SUBJECT_MATCH}" after:{after_epoch}'
