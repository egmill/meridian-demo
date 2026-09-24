import pytest
from fastapi.testclient import TestClient

from app import main
from app.store import CustomerStore


@pytest.fixture
def store(monkeypatch) -> CustomerStore:
    fresh = CustomerStore()
    monkeypatch.setattr(main, "store", fresh)
    return fresh


@pytest.fixture
def client(store) -> TestClient:
    return TestClient(main.app)
