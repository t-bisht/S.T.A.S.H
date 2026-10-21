"""Reusable, source-agnostic Gmail reading layer.

Owns *all* the mechanics of reading Gmail — paginated search, full fetch, base64url decode,
MIME walking, HTML→text stripping — so a source handler never writes HTTP or MIME code. Built
for Swiggy first, reused verbatim by every future Gmail-backed source. See spec §10a.

The client is a thin, token-injected client: the caller passes a valid access token on each
call; the client does no token management of its own (that is the broker's job, spec §5).
"""

from __future__ import annotations

__all__: list[str] = []
