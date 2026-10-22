"""The handler registry: maps a ``scan_source_name`` to its parse handler class (spec §2.3).

The orchestrator looks up ``scan_sources.scan_source_name`` → ``HANDLERS[name]`` → boots a
scheduler for it. A ``scan_sources`` row whose name has no entry here is logged and skipped —
a clear misconfiguration signal, never a crash. Adding a source = a new handler package + one
line here + a ``scan_sources`` row (no edit to llm/, orchestrator, or scheduler).
"""

from __future__ import annotations

from pen.core.handler import ParseHandler
from pen.handlers.swiggy.handler import SwiggyHandler

__all__ = ["HANDLERS"]

HANDLERS: dict[str, type[ParseHandler]] = {
    SwiggyHandler.source_name: SwiggyHandler,
    # Add new sources here (code change required — see spec §2.3).
}
