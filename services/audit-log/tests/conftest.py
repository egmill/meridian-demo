from datetime import datetime, timezone

import pytest
from fastapi.testclient import TestClient

from app import main, store as store_module
from app.store import AuditStore

FIXED_NOW = datetime(2026, 1, 15, 12, 0, 0, tzinfo=timezone.utc)


class FixedDatetime(datetime):
    @classmethod
    def now(cls, tz=None):
        return FIXED_NOW if tz is None else FIXED_NOW.astimezone(tz)


@pytest.fixture(autouse=True)
def fixed_clock(monkeypatch):
    monkeypatch.setattr(store_module, "datetime", FixedDatetime)
    return FIXED_NOW


@pytest.fixture
def audit_store():
    return AuditStore()


@pytest.fixture
def fresh_store(monkeypatch):
    new_store = AuditStore()
    monkeypatch.setattr(main, "store", new_store)
    return new_store


@pytest.fixture
def client(fresh_store):
    return TestClient(main.app)
