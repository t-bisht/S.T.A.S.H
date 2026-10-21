"""Reusable Gmail REST client: search, fetch, decode, strip.

Source-agnostic (spec §10a). A handler supplies only a query string and a mail→row mapping;
everything about *how* to talk to Gmail lives here:

- :meth:`GmailClient.search_all_ids` — full pagination over ``nextPageToken``, 500 ids/page,
  no truncation.
- :meth:`GmailClient.fetch_message` — ``format=full`` → MIME walk → base64url decode → prefer
  ``text/plain`` else ``text/html`` → :func:`html_to_text` → :class:`ParsedMessage`.
- :func:`html_to_text` — pure helper; strips non-content tags, flattens, collapses blanks.

On a Gmail HTTP 401 the client raises :class:`GmailUnauthorized`; the framework's
``with_gmail_token`` helper (``tokens.broker``) catches it to trigger a one-shot token refresh.
Other HTTP / transport failures raise :class:`GmailError`.
"""

from __future__ import annotations

import base64
import logging
import re
from dataclasses import dataclass
from datetime import UTC, datetime

import requests

__all__ = [
    "GmailClient",
    "GmailError",
    "GmailUnauthorized",
    "ParsedMessage",
    "html_to_text",
]

log = logging.getLogger(__name__)

_GMAIL_BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
_PAGE_SIZE = 500  # Gmail's max page size for messages.list.
_SEARCH_TIMEOUT = 15
_FETCH_TIMEOUT = 20

# Tags that never carry order content — removed wholesale before extracting text.
_NON_CONTENT_TAGS = ("script", "style", "head", "title", "meta", "link", "noscript")


class GmailError(RuntimeError):
    """A Gmail API call failed for a non-auth reason (5xx, transport error, bad body)."""


class GmailUnauthorized(GmailError):
    """Gmail returned HTTP 401 — the access token was rejected and must be refreshed."""


@dataclass(frozen=True, slots=True)
class ParsedMessage:
    """A fetched, decoded, stripped Gmail message.

    Attributes:
        message_id: Gmail message id — the dedup key.
        received_at: Arrival time from ``internalDate`` (epoch ms → UTC).
        subject: ``Subject`` header, or empty string.
        from_addr: ``From`` header, or empty string.
        body_text: HTML-stripped, LLM-ready clean text (raw body never surfaced).
    """

    message_id: str
    received_at: datetime
    subject: str
    from_addr: str
    body_text: str


class GmailClient:
    """Thin Gmail REST client. One instance is shared across users and sources.

    A :class:`requests.Session` is reused for connection pooling. Tokens are passed per call —
    the client holds no credentials.
    """

    def __init__(self, session: requests.Session | None = None) -> None:
        """Create a client.

        Args:
            session: Optional shared :class:`requests.Session`; one is created if omitted.
        """
        self._session = session or requests.Session()

    def search_all_ids(self, token: str, query: str) -> list[str]:
        """Return EVERY message id matching ``query``, across all pages.

        Follows ``nextPageToken`` until Gmail returns none, accumulating ids at 500/page. Ids
        only — cheap; no body fetch here, no truncation, no page cap (any backfill size works).

        Args:
            token: A valid Google access token.
            query: A Gmail search query string.

        Returns:
            All matching message ids (possibly empty).

        Raises:
            GmailUnauthorized: On HTTP 401 (token rejected).
            GmailError: On any other Gmail / transport failure.
        """
        ids: list[str] = []
        page_token: str | None = None
        while True:
            params: dict[str, str | int] = {"q": query, "maxResults": _PAGE_SIZE}
            if page_token:
                params["pageToken"] = page_token
            body = self._get(f"{_GMAIL_BASE}/messages", token, params, _SEARCH_TIMEOUT)
            # `messages` is absent (not empty) when nothing matches on a page.
            ids.extend(message["id"] for message in body.get("messages", []))
            page_token = body.get("nextPageToken")
            if not page_token:
                break
        return ids

    def fetch_message(self, token: str, message_id: str) -> ParsedMessage:
        """Fetch one message in full and return it decoded + stripped.

        Args:
            token: A valid Google access token.
            message_id: The Gmail message id to fetch.

        Returns:
            A :class:`ParsedMessage` with clean ``body_text``.

        Raises:
            GmailUnauthorized: On HTTP 401 (token rejected).
            GmailError: On any other Gmail / transport failure, or a malformed response.
        """
        body = self._get(
            f"{_GMAIL_BASE}/messages/{message_id}",
            token,
            {"format": "full"},
            _FETCH_TIMEOUT,
        )
        payload = body.get("payload", {})
        headers = {h.get("name", ""): h.get("value", "") for h in payload.get("headers", [])}

        try:
            # internalDate is epoch millis (UTC) — reliable, unlike the free-form Date header.
            received_at = datetime.fromtimestamp(int(body["internalDate"]) / 1000, tz=UTC)
        except (KeyError, ValueError, TypeError) as exc:
            raise GmailError(f"message {message_id} missing/invalid internalDate") from exc

        raw_body = _extract_body(payload)
        return ParsedMessage(
            message_id=message_id,
            received_at=received_at,
            subject=headers.get("Subject", ""),
            from_addr=headers.get("From", ""),
            body_text=html_to_text(raw_body),
        )

    def _get(
        self,
        url: str,
        token: str,
        params: dict[str, str | int],
        timeout: int,
    ) -> dict:
        """Issue an authenticated GET and return the parsed JSON body.

        Centralises auth-header injection and status-to-exception mapping for every call.
        """
        try:
            resp = self._session.get(
                url,
                headers={"Authorization": f"Bearer {token}"},
                params=params,
                timeout=timeout,
            )
        except requests.RequestException as exc:
            raise GmailError(f"Gmail request failed: {exc}") from exc

        if resp.status_code == 401:
            raise GmailUnauthorized("Gmail rejected the access token (401)")
        if resp.status_code >= 400:
            raise GmailError(f"Gmail returned HTTP {resp.status_code} for {url}")

        try:
            return resp.json()
        except ValueError as exc:
            raise GmailError(f"Gmail returned a non-JSON body for {url}") from exc


def _b64url_decode(data: str) -> str:
    """Decode Gmail's URL-safe base64 body, tolerating missing padding."""
    padded = data + "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode(padded).decode("utf-8", errors="replace")


def _extract_body(payload: dict) -> str:
    """Return the message body: ``text/plain`` if present, else ``text/html``, else ``""``.

    Walks the MIME tree recursively, decoding the first ``text/plain`` and ``text/html`` parts.
    """
    collected: dict[str, str] = {}
    _collect_parts(payload, collected)
    return collected.get("text/plain") or collected.get("text/html") or ""


def _collect_parts(payload: dict, acc: dict[str, str]) -> None:
    """Recursively gather the first ``text/plain`` and ``text/html`` part bodies into ``acc``."""
    mime = payload.get("mimeType", "")
    data = payload.get("body", {}).get("data")
    if data and mime in ("text/plain", "text/html") and mime not in acc:
        acc[mime] = _b64url_decode(data)
    for part in payload.get("parts", []) or []:
        _collect_parts(part, acc)


def html_to_text(body: str) -> str:
    """Strip an HTML (or plain) email body down to clean, LLM-ready text.

    Removes non-content tags wholesale (``script``, ``style``, ``head`` …), flattens to text
    with newlines between blocks, trims each line, drops blank lines, and collapses runs of
    spaces. Returns ``""`` for an empty body.

    Args:
        body: Raw message body (HTML or plain text).

    Returns:
        Normalised plain text.
    """
    if not body:
        return ""
    # Imported lazily so the pure helpers above don't pull bs4 into import-light contexts.
    from bs4 import BeautifulSoup

    soup = BeautifulSoup(body, "html.parser")
    for tag in soup(_NON_CONTENT_TAGS):
        tag.decompose()  # remove the tag and its contents entirely
    text = soup.get_text(separator="\n")
    lines = [re.sub(r"[ \t\xa0]+", " ", line).strip() for line in text.splitlines()]
    return "\n".join(line for line in lines if line)
