"""Reusable token-broker component.

Talks to ``user_engine``'s server-to-server ``/internal/**`` routes to obtain Google access
tokens for a user, and forces a refresh after a Gmail 401. Shared by ALL sources — Swiggy is
only the first caller. See spec §5.
"""

from __future__ import annotations

__all__: list[str] = []
