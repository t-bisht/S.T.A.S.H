"""The shared retry / rate-limit-pause wrapper (spec §4.3, §11.1).

Every handler's parse call goes through :func:`run_with_retry`, so the policy lives in exactly
one place:

- :class:`~pen.llm.exceptions.LLMRateLimited` (429) → pause ``rate_limit_pause_seconds`` (the
  free-tier window is per-minute; a sub-minute retry just re-hits it), then retry.
- :class:`~pen.llm.exceptions.LLMResponseInvalid` → **permanent**, re-raised immediately (a
  retry would reproduce the same unparseable output) → the caller parks ``invalid_json``.
- :class:`~pen.llm.exceptions.LLMError` (other transport/5xx) → short exponential backoff,
  then retry.
- Anything else (e.g. the handler's validation errors) → propagated untouched; retry does not
  catch domain errors.

The pause/backoff waits are interruptible: they wait on the ``stop_event`` so a SIGTERM during
a 60s pause exits promptly instead of blocking the whole minute.
"""

from __future__ import annotations

import logging
import threading
from collections.abc import Callable

from pen.llm.exceptions import LLMError, LLMRateLimited, LLMResponseInvalid

__all__ = ["run_with_retry"]

# Base for the exponential backoff on transient (non-429) errors: 1s, 2s, 4s, …
_BACKOFF_BASE_SECONDS = 1.0


def run_with_retry[T](
    fn: Callable[[], T],
    *,
    retries: int = 3,
    rate_limit_pause_seconds: int = 60,
    stop_event: threading.Event | None = None,
    log: logging.LoggerAdapter | logging.Logger | None = None,
) -> T:
    """Run ``fn`` with the shared transient-retry + 429-pause policy.

    Args:
        fn: The zero-arg parse call to run (typically ``lambda: parse(ctx, row)``).
        retries: Max attempts before giving up (spec default ``3``).
        rate_limit_pause_seconds: Pause before retrying a 429 (spec default ``60``).
        stop_event: If set during a wait, aborts further retries and re-raises the last error
            (clean shutdown).
        log: Logger for retry/pause visibility (spec §12).

    Returns:
        Whatever ``fn`` returns on the first successful attempt.

    Raises:
        LLMResponseInvalid: Immediately, without retrying (permanent).
        LLMRateLimited | LLMError: If the final attempt still fails.
        Exception: Any non-LLM error from ``fn`` is propagated on first occurrence.
    """
    logger = log or logging.getLogger(__name__)
    last_exc: Exception | None = None

    for attempt in range(1, retries + 1):
        try:
            return fn()
        except LLMRateLimited as exc:
            last_exc = exc
            logger.warning(
                "rate limited (attempt %d/%d) — pausing %ds before retry",
                attempt,
                retries,
                rate_limit_pause_seconds,
            )
            if attempt < retries and not _wait(stop_event, rate_limit_pause_seconds):
                continue
            raise
        except LLMResponseInvalid:
            # Permanent: retrying reproduces the same unparseable output. Don't retry.
            raise
        except LLMError as exc:
            last_exc = exc
            backoff = _BACKOFF_BASE_SECONDS * (2 ** (attempt - 1))
            logger.warning(
                "transient LLM error (attempt %d/%d): %s — backoff %.1fs",
                attempt,
                retries,
                exc,
                backoff,
            )
            if attempt < retries and not _wait(stop_event, backoff):
                continue
            raise

    # Unreachable in practice (the loop raises on the final attempt), but satisfies typing.
    assert last_exc is not None
    raise last_exc


def _wait(stop_event: threading.Event | None, seconds: float) -> bool:
    """Interruptible wait; returns ``True`` if a shutdown was requested during the wait."""
    if stop_event is None:
        threading.Event().wait(seconds)
        return False
    return stop_event.wait(seconds)
