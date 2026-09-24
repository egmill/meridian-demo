"""GET /customers/{id} masking and routing edge cases. All data is synthetic."""

import pytest
from fastapi.testclient import TestClient

from app import main
from app.store import CustomerStore

FULLY_MASKED = "***-**-****"


@pytest.fixture
def raw_client(store: CustomerStore) -> TestClient:
    """Client that surfaces server exceptions as 500 responses instead of raising."""
    return TestClient(main.app, raise_server_exceptions=False)


def test_get_customer_preserves_leading_zero_in_last4(client: TestClient, store: CustomerStore):
    created = store.create("Zero", "zero@example.com", "123-45-0007")
    body = client.get(f"/customers/{created.id}").json()
    assert body["ssnMasked"] == "***-**-0007"
    assert body["ssnLast4"] == "0007"


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: GET /customers/{id} echoes the raw SSN in ssnMasked when the "
    "stored SSN is not in NNN-NN-NNNN format",
)
def test_get_customer_fully_masks_unformatted_stored_ssn(
    client: TestClient, store: CustomerStore
):
    created = store.create("Unformatted", "u@example.com", "123456789")
    body = client.get(f"/customers/{created.id}").json()
    assert body["ssnMasked"] == FULLY_MASKED
    assert "123456789" not in body.values()


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: GET /customers/{id} returns 500 when the stored SSN has no "
    "digits because ssn_last4 raises ValueError (masking must never crash)",
)
def test_get_customer_with_digitless_stored_ssn_does_not_crash(
    raw_client: TestClient, store: CustomerStore
):
    created = store.create("Broken", "b@example.com", "not-an-ssn")
    resp = raw_client.get(f"/customers/{created.id}")
    assert resp.status_code == 200
    assert resp.json()["ssnMasked"] == FULLY_MASKED
    assert resp.json()["ssnLast4"] == "****"


def test_delete_customer_not_allowed(client: TestClient):
    assert client.delete("/customers/C-1001").status_code == 405


def test_unknown_route_returns_404(client: TestClient):
    assert client.get("/nope").status_code == 404


def test_cors_preflight_rejects_disallowed_method(client: TestClient):
    resp = client.options(
        "/customers",
        headers={
            "Origin": "http://localhost:3000",
            "Access-Control-Request-Method": "DELETE",
        },
    )
    assert resp.status_code == 400
