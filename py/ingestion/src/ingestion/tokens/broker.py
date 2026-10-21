"""``user_engine`` token broker + the framework-level refresh-once-on-401 helper.

The broker is the only component that talks to ``user_engine``'s ``/internal/**`` token routes
(guarded by the ``X-Internal-Auth`` shared secret). It exposes two calls (spec §5):

- :meth:`UserEngineTokenBroker.get_access_token` — the normal path; ``user_engine`` refreshes
  inline only if the stored token is near expiry. Never force a refresh speculatively.
- :meth:`UserEngineTokenBroker.refresh` — forces a refresh; called ONLY after a Gmail 401.

Status codes are mapped to typed exceptions straight from the ``user_engine`` source
(``TokenRefreshAdvice`` / ``InternalAuthFilter``):

    | HTTP | error code            | raised                       |
    |------|-----------------------|------------------------------|
    | 200  | —                     | returns the access token     |
    | 401  | unauthorized          | TokenError("internal_auth")  |
    | 404  | account_not_found     | TokenError("account_not_found") |
    | 409  | login_required        | ReauthRequired               |
    | 503  | refresh_unavailable   | TokenError("refresh_unavailable") |
    | 4xx/5xx other | (body code)  | TokenError(<code>)           |
"""

from __future__ import annotations

import logging
from uuid import UUID

import requests

from ingestion.gmail.client import GmailUnauthorized
from ingestion.tokens.exceptions import ReauthRequired, TokenError

__all__ = ["TokenBroker", "UserEngineTokenBroker", "with_gmail_token"]

log = logging.getLogger(__name__)

_PROVIDER = "google"  # user_engine supports only google today.
_GET_TIMEOUT = 10
_REFRESH_TIMEOUT = 15

# user_engine error codes (TokenRefreshAdvice) that map to a dead refresh token.
_REAUTH_CODE = "login_required"


class TokenBroker:
    """Structural interface a source uses to obtain tokens.

    Defined for documentation and typing; :class:`UserEngineTokenBroker` is the concrete v1
    implementation. A test double need only provide these two methods.
    """

    def get_access_token(self, user_id: UUID) -> str:  # pragma: no cover - interface
        raise NotImplementedError

    def refresh(self, user_id: UUID) -> str:  # pragma: no cover - interface
        raise NotImplementedError


class UserEngineTokenBroker(TokenBroker):
    """Concrete broker backed by ``user_engine``'s ``/internal/**`` routes."""

    def __init__(
        self,
        base_url: str,
        internal_svc_token: str,
        *,
        session: requests.Session | None = None,
    ) -> None:
        """Create the broker.

        Args:
            base_url: ``user_engine`` base URL (e.g. ``http://localhost:7083``), no trailing slash.
            internal_svc_token: Shared secret sent as ``X-Internal-Auth``. Secret — never logged.
            session: Optional shared :class:`requests.Session` for connection pooling.
        """
        self._base_url = base_url.rstrip("/")
        self._headers = {"X-Internal-Auth": internal_svc_token}
        self._session = session or requests.Session()

    def get_access_token(self, user_id: UUID) -> str:
        """Return a currently-valid access token for the user.

        ``user_engine`` refreshes inline only if the stored token is about to expire.

        Raises:
            ReauthRequired: If ``user_engine`` reports the refresh token is dead (409).
            TokenError: For any other broker failure.
        """
        url = f"{self._base_endpoint(user_id)}/access-token"
        resp = self._request("GET", url, _GET_TIMEOUT)
        return self._token_from(resp, user_id, action="get_access_token")

    def refresh(self, user_id: UUID) -> str:
        """Force a refresh and return the freshly minted token. Call only after a Gmail 401.

        Raises:
            ReauthRequired: If the refresh token is dead (409).
            TokenError: For any other broker failure.
        """
        url = f"{self._base_endpoint(user_id)}/refresh"
        resp = self._request("POST", url, _REFRESH_TIMEOUT)
        return self._token_from(resp, user_id, action="refresh")

    def _base_endpoint(self, user_id: UUID) -> str:
        return f"{self._base_url}/internal/users/{user_id}/oauth/{_PROVIDER}"

    def _request(self, method: str, url: str, timeout: int) -> requests.Response:
        """Issue the HTTP call, mapping transport failures to :class:`TokenError`."""
        try:
            return self._session.request(method, url, headers=self._headers, timeout=timeout)
        except requests.RequestException as exc:
            raise TokenError("broker_unreachable", f"user_engine unreachable: {exc}") from exc

    def _token_from(self, resp: requests.Response, user_id: UUID, *, action: str) -> str:
        """Map a ``user_engine`` response to an access token or a typed exception."""
        if resp.status_code == 200:
            try:
                return resp.json()["access_token"]
            except (ValueError, KeyError) as exc:
                raise TokenError(
                    "invalid_broker_response", "user_engine 200 without access_token"
                ) from exc

        code = _error_code(resp)
        if resp.status_code == 409 or code == _REAUTH_CODE:
            # Dead refresh token — user must re-link. Logged by the runner, not here.
            raise ReauthRequired()

        # Everything else is a per-user failure with a stable, queryable code.
        log.warning(
            "token_broker.%s failed user=%s http=%s code=%s",
            action,
            user_id,
            resp.status_code,
            code,
        )
        raise TokenError(code, f"user_engine {action} returned HTTP {resp.status_code}")


def _error_code(resp: requests.Response) -> str:
    """Extract the stable ``error`` code from a ``user_engine`` error body.

    Falls back to a synthetic ``http_<status>`` code when the body is absent or not the
    expected ``{error, error_description}`` shape.
    """
    try:
        body = resp.json()
    except ValueError:
        return f"http_{resp.status_code}"
    if isinstance(body, dict) and isinstance(body.get("error"), str):
        return body["error"]
    return f"http_{resp.status_code}"


def with_gmail_token(broker: TokenBroker, user_id: UUID, gmail_call):
    """Run a Gmail call with a valid token, refreshing once on a 401.

    Starts with :meth:`TokenBroker.get_access_token` (never a speculative refresh). If the
    Gmail call raises :class:`GmailUnauthorized` (HTTP 401), forces one refresh and retries the
    call once. A second 401 propagates as a failure.

    Args:
        broker: The token broker.
        user_id: The user whose token to use.
        gmail_call: A one-arg callable ``(token: str) -> T`` performing the Gmail request.

    Returns:
        Whatever ``gmail_call`` returns.

    Raises:
        ReauthRequired: If the refresh token is dead (surfaced to the runner, which stops this
            user's scan and records the failure).
        TokenError: For other broker failures.
        GmailUnauthorized: If Gmail still returns 401 after a fresh token.
        GmailError: For other Gmail failures raised by ``gmail_call``.
    """
    token = broker.get_access_token(user_id)
    try:
        return gmail_call(token)
    except GmailUnauthorized:
        # Token rejected mid-use — force exactly one refresh, then retry once.
        token = broker.refresh(user_id)  # may raise ReauthRequired (409)
        return gmail_call(token)
