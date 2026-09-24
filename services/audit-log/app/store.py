"""Append-only audit event store.

Mirrors the column limits of the production audit table so behavior
matches between environments.
"""

from datetime import datetime, timezone
from itertools import count
from threading import Lock

MAX_ACTOR_LENGTH = 64
MAX_ACTION_LENGTH = 32
MAX_DETAILS_LENGTH = 128


class AuditStore:
    def __init__(self) -> None:
        self._events: list[dict] = []
        self._ids = count(1)
        self._lock = Lock()

    def append(self, actor: str, action: str, details: str) -> dict:
        if len(actor) > MAX_ACTOR_LENGTH:
            raise ValueError("actor exceeds column width")
        if len(action) > MAX_ACTION_LENGTH:
            raise ValueError("action exceeds column width")
        if len(details) > MAX_DETAILS_LENGTH:
            raise ValueError("details exceeds column width")

        with self._lock:
            event = {
                "id": next(self._ids),
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "actor": actor,
                "action": action,
                "details": details,
            }
            self._events.append(event)
        return event

    def list(self, limit: int = 100) -> list[dict]:
        return list(reversed(self._events[-limit:]))
