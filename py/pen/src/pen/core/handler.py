"""The ``ParseHandler`` contract + the per-pass result (spec §11).

A handler is a sub-orchestrator: given a :class:`~pen.core.context.ParseContext`, it drains its
source's unparsed candidates one row per transaction, applying the shared retry/pause/park
policy, and returns a :class:`RunSummary` of counts. The scheduler turns that summary into the
per-pass metadata row (§4.5) and the summary log line (§12).
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field

from pen.core.context import ParseContext

__all__ = ["ParseHandler", "RunSummary"]


@dataclass(slots=True)
class RunSummary:
    """Tally of one source pass, emitted as the per-run summary line (spec §12).

    Attributes:
        scan_source_name: The source this pass covered.
        run_id: Correlation id for the pass.
        candidates: Rows selected for this pass.
        parsed: Rows parsed + persisted successfully.
        parked: Rows parked in ``parse_failures`` (terminal for this pass).
        persist_errors: Rows that parsed but failed to persist (transient — NOT parked; retried
            next pass).
        parked_by_reason: Park count broken down by §4.4 reason.
    """

    scan_source_name: str
    run_id: str
    candidates: int = 0
    parsed: int = 0
    parked: int = 0
    persist_errors: int = 0
    parked_by_reason: dict[str, int] = field(default_factory=dict)

    def record_park(self, reason: str) -> None:
        """Count one parked row under its reason."""
        self.parked += 1
        self.parked_by_reason[reason] = self.parked_by_reason.get(reason, 0) + 1


class ParseHandler(ABC):
    """Base class for a source parse handler (the sub-orchestrator contract).

    A concrete handler sets :attr:`source_name` to its ``scan_sources.scan_source_name`` (the
    registry key) and implements :meth:`run`.
    """

    source_name: str

    @abstractmethod
    def run(self, ctx: ParseContext) -> RunSummary:
        """Sub-orchestrate scan → parse → persist for this source's candidates.

        One row per transaction, with retry/pause/park (spec §4.3–4.4). Must never raise for a
        single bad row — a row failure is terminal-and-recorded (parked) or transient (left for
        the next pass); the pass continues. It may raise only when the pass *cannot proceed at
        all* (e.g. the initial candidate read fails), which the scheduler records as a FAILED
        run (§4.5).

        Returns:
            The pass's :class:`RunSummary`.
        """
        raise NotImplementedError
