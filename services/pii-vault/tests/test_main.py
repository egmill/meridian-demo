"""Tests for app/main.py HTTP endpoints via FastAPI TestClient. All data is synthetic."""

import pytest

from app.main import to_profile
from app.store import Customer

VALID_BODY = {"name": "Test User", "email": "test@example.com", "ssn": "111-22-3333"}


# --- to_profile -------------------------------------------------------------


def test_to_profile_masks_ssn_and_exposes_last4():
    profile = to_profile(Customer("C-1", "N", "n@example.com", "111-22-3333"))
    assert profile == {
        "id": "C-1",
        "name": "N",
        "email": "n@example.com",
        "ssnMasked": "***-**-3333",
        "ssnLast4": "3333",
    }


def test_to_profile_never_includes_raw_ssn_key():
    profile = to_profile(Customer("C-1", "N", "n@example.com", "111-22-3333"))
    assert "ssn" not in profile
    assert "111-22-3333" not in profile.values()


# --- GET /customers ---------------------------------------------------------


def test_list_customers_returns_seeded_customers(client):
    response = client.get("/customers")
    assert response.status_code == 200
    assert response.json() == [
        {"id": "C-1001", "name": "Avery Chen"},
        {"id": "C-1002", "name": "Marcus Webb"},
        {"id": "C-1003", "name": "Priya Natarajan"},
    ]


def test_list_customers_does_not_expose_pii(client):
    response = client.get("/customers")
    for item in response.json():
        assert set(item) == {"id", "name"}
    assert "@" not in response.text
    assert "-45-" not in response.text


def test_list_customers_includes_newly_created(client):
    client.post("/customers", json=VALID_BODY)
    ids = [c["id"] for c in client.get("/customers").json()]
    assert ids == ["C-1001", "C-1002", "C-1003", "C-1004"]


def test_list_customers_rejects_unsupported_method(client):
    assert client.delete("/customers").status_code == 405


# --- GET /customers/{id} ----------------------------------------------------


def test_get_customer_returns_masked_profile(client):
    response = client.get("/customers/C-1001")
    assert response.status_code == 200
    assert response.json() == {
        "id": "C-1001",
        "name": "Avery Chen",
        "email": "avery.chen@example.com",
        "ssnMasked": "***-**-6789",
        "ssnLast4": "6789",
    }


def test_get_customer_never_returns_raw_ssn(client):
    response = client.get("/customers/C-1002")
    assert "987-65-4321" not in response.text
    assert "ssn" not in response.json()


def test_get_customer_unknown_id_returns_404(client):
    response = client.get("/customers/C-9999")
    assert response.status_code == 404
    assert response.json() == {"detail": "Customer not found"}


def test_get_customer_id_lookup_is_case_sensitive(client):
    assert client.get("/customers/c-1001").status_code == 404


def test_get_customer_with_path_traversal_like_id_returns_404(client):
    assert client.get("/customers/..%2F..").status_code == 404


# --- POST /customers --------------------------------------------------------


def test_create_customer_returns_201_with_masked_profile(client):
    response = client.post("/customers", json=VALID_BODY)
    assert response.status_code == 201
    assert response.json() == {
        "id": "C-1004",
        "name": "Test User",
        "email": "test@example.com",
        "ssnMasked": "***-**-3333",
        "ssnLast4": "3333",
    }


def test_create_customer_response_never_contains_raw_ssn(client):
    response = client.post("/customers", json=VALID_BODY)
    assert VALID_BODY["ssn"] not in response.text


def test_create_customer_persists_to_store(client, store):
    created_id = client.post("/customers", json=VALID_BODY).json()["id"]
    stored = store.get(created_id)
    assert stored is not None
    assert stored.ssn == VALID_BODY["ssn"]


def test_create_customer_then_get_round_trip(client):
    created_id = client.post("/customers", json=VALID_BODY).json()["id"]
    fetched = client.get(f"/customers/{created_id}")
    assert fetched.status_code == 200
    assert fetched.json()["ssnMasked"] == "***-**-3333"


@pytest.mark.parametrize("missing", ["name", "email", "ssn"])
def test_create_customer_missing_field_returns_422(client, missing):
    body = {k: v for k, v in VALID_BODY.items() if k != missing}
    response = client.post("/customers", json=body)
    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["body", missing]


@pytest.mark.parametrize("field,value", [("name", 123), ("email", None), ("ssn", ["1"])])
def test_create_customer_wrong_type_returns_422(client, field, value):
    body = {**VALID_BODY, field: value}
    assert client.post("/customers", json=body).status_code == 422


def test_create_customer_empty_body_returns_422(client):
    assert client.post("/customers", json={}).status_code == 422


def test_create_customer_non_json_body_returns_422(client):
    response = client.post(
        "/customers", content="not json", headers={"Content-Type": "application/json"}
    )
    assert response.status_code == 422


def test_create_customer_ignores_unknown_fields(client):
    response = client.post("/customers", json={**VALID_BODY, "id": "C-0001", "role": "x"})
    assert response.status_code == 201
    assert response.json()["id"] == "C-1004"


def test_create_customer_validation_failure_does_not_create_record(client, store):
    client.post("/customers", json={"name": "x"})
    assert len(store.list()) == 3


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: malformed SSN '123456789' is echoed back unmasked in "
    "ssnMasked; business rule requires full masking of unexpected formats",
)
def test_create_customer_with_undashed_ssn_is_fully_masked(client):
    response = client.post("/customers", json={**VALID_BODY, "ssn": "123456789"})
    assert response.status_code == 201
    assert response.json()["ssnMasked"] == "***-**-****"
    assert "123456789" not in response.text


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: POST /customers with an SSN containing no digits returns "
    "500 (ssn_last4 raises ValueError); masking must never crash",
)
def test_create_customer_with_non_numeric_ssn_does_not_crash(client):
    response = client.post("/customers", json={**VALID_BODY, "ssn": "not-an-ssn"})
    assert response.status_code in (201, 422)


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: SSN shorter than 4 digits is returned raw in ssnMasked and "
    "zero-padded in ssnLast4 instead of being fully masked",
)
def test_create_customer_with_short_ssn_is_fully_masked(client):
    response = client.post("/customers", json={**VALID_BODY, "ssn": "12"})
    assert response.status_code == 201
    assert response.json()["ssnMasked"] == "***-**-****"
    assert response.json()["ssnLast4"] == "****"


# --- CORS -------------------------------------------------------------------


def test_cors_allows_configured_web_origin(client):
    response = client.options(
        "/customers",
        headers={
            "Origin": "http://localhost:3000",
            "Access-Control-Request-Method": "GET",
        },
    )
    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == "http://localhost:3000"


def test_cors_rejects_unknown_origin(client):
    response = client.get("/customers", headers={"Origin": "http://evil.example.com"})
    assert "access-control-allow-origin" not in response.headers
