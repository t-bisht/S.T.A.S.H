"""Typed exceptions raised by the token broker.

These drive runner behaviour (spec §5, §8):

- :class:`ReauthRequired` — the refresh token itself is dead (user revoked / must re-link).
  The runner stops that user's scan and records ``last_error = "login_required"``; other users
  continue. Not retryable until the user re-authenticates.
- :class:`TokenError` — any other broker failure (no linked account, broker transiently
  unavailable, bad shared secret, malformed response). Caught at the per-user boundary; the
  user's scan is marked failed and the runner moves on.
"""

from __future__ import annotations

__all__ = ["ReauthRequired", "TokenError"]


class TokenError(RuntimeError):
    """A token could not be obtained from ``user_engine`` for a non-reauth reason.

    Attributes:
        code: Short stable reason, suitable for ``source_scan_run_metadata.last_error``
            (e.g. ``account_not_found``, ``refresh_unavailable``, ``internal_error``).
    """

    def __init__(self, code: str, message: str | None = None) -> None:
        self.code = code
        super().__init__(message or code)


class ReauthRequired(TokenError):
    """The user's refresh token is dead — they must sign in again (``user_engine`` 409).

    Carries the fixed code ``login_required`` so the runner records a stable, queryable reason.
    """

    def __init__(self, message: str | None = None) -> None:
        super().__init__("login_required", message or "user must sign in again")
