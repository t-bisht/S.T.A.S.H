"""The source registry: maps a stable ``scan_source_name`` to its handler class.

This dict is the dispatch contract (spec §2): the runner looks up ``row.scan_source_name`` →
``HANDLERS[name]`` → runs the handler. A ``scan_sources`` row whose name has no entry here is
logged and skipped (a clear misconfig signal, never a crash). Adding a new source is a code
change here plus a DB row — see spec §2 "Adding a new source".
"""

from __future__ import annotations

from ingestion.sources.base import SourceHandler
from ingestion.sources.swiggy_instamart.handler import SwiggyInstamartHandler

__all__ = ["HANDLERS"]

HANDLERS: dict[str, type[SourceHandler]] = {
    SwiggyInstamartHandler.source_name: SwiggyInstamartHandler,
    # Add new sources here (code change required — see spec §2).
}
