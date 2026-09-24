import logging

import pytest

from app import main
from app.store import MAX_ACTION_LENGTH, MAX_ACTOR_LENGTH, MAX_DETAILS_LENGTH

VALID_EVENT = {"actor": "user-1", "action": "LOGIN", "details": "mfa ok"}


class FailingStore:
    def append(self, actor, action, details):
        raise RuntimeError("storage unavailable")

    def list(self, limit=100):
        return []


# --- POST /events: happy path ------------------------------------------------


def test_post_event_returns_201_recorded(client):
    response = client.post("/events", json=VALID_EVENT)

    assert response.status_code == 201
    assert response.json() == {"status": "recorded"}


def test_post_event_is_persisted_with_submitted_fields(client, fresh_store, fixed_clock):
    client.post("/events", json=VALID_EVENT)

    assert fresh_store.list() == [
        {"id": 1, "timestamp": fixed_clock.isoformat(), **VALID_EVENT}
    ]


def test_post_event_ignores_unknown_fields(client, fresh_store):
    response = client.post("/events", json={**VALID_EVENT, "id": 999, "extra": "x"})

    assert response.status_code == 201
    assert fresh_store.list()[0]["id"] == 1


@pytest.mark.parametrize(
    ("field", "limit"),
    [("actor", MAX_ACTOR_LENGTH), ("action", MAX_ACTION_LENGTH), ("details", MAX_DETAILS_LENGTH)],
)
def test_post_event_accepts_field_at_column_width(client, fresh_store, field, limit):
    response = client.post("/events", json={**VALID_EVENT, field: "x" * limit})

    assert response.status_code == 201
    assert fresh_store.list()[0][field] == "x" * limit


# --- POST /events: input validation -----------------------------------------


@pytest.mark.parametrize("missing", ["actor", "action", "details"])
def test_post_event_missing_required_field_returns_422(client, fresh_store, missing):
    body = {k: v for k, v in VALID_EVENT.items() if k != missing}

    response = client.post("/events", json=body)

    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["body", missing]
    assert fresh_store.list() == []


@pytest.mark.parametrize("field", ["actor", "action", "details"])
def test_post_event_non_string_field_returns_422(client, fresh_store, field):
    response = client.post("/events", json={**VALID_EVENT, field: 123})

    assert response.status_code == 422
    assert fresh_store.list() == []


@pytest.mark.parametrize("field", ["actor", "action", "details"])
def test_post_event_null_field_returns_422(client, fresh_store, field):
    response = client.post("/events", json={**VALID_EVENT, field: None})

    assert response.status_code == 422
    assert fresh_store.list() == []


def test_post_event_empty_body_returns_422(client):
    response = client.post("/events", json={})

    assert response.status_code == 422


def test_post_event_malformed_json_returns_422(client, fresh_store):
    response = client.post(
        "/events", content=b"{not json", headers={"content-type": "application/json"}
    )

    assert response.status_code == 422
    assert fresh_store.list() == []


# --- POST /events: error handling -------------------------------------------


@pytest.mark.parametrize(
    ("field", "limit"),
    [("actor", MAX_ACTOR_LENGTH), ("action", MAX_ACTION_LENGTH), ("details", MAX_DETAILS_LENGTH)],
)
def test_post_event_over_column_width_stores_nothing(client, fresh_store, field, limit):
    client.post("/events", json={**VALID_EVENT, field: "x" * (limit + 1)})

    assert fresh_store.list() == []


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: POST /events returns 201 'recorded' when the store "
    "rejects an over-width field, so the audit record is silently lost "
    "(main.py:31-35 swallows the exception)",
)
@pytest.mark.parametrize(
    ("field", "limit"),
    [("actor", MAX_ACTOR_LENGTH), ("action", MAX_ACTION_LENGTH), ("details", MAX_DETAILS_LENGTH)],
)
def test_post_event_over_column_width_returns_error(client, field, limit):
    response = client.post("/events", json={**VALID_EVENT, field: "x" * (limit + 1)})

    assert response.status_code >= 400
    assert response.json() != {"status": "recorded"}


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: POST /events returns 201 'recorded' when the store "
    "raises on write; audit write failures must fail the request "
    "(main.py:31-35 swallows the exception)",
)
def test_post_event_store_failure_returns_error(client, monkeypatch):
    monkeypatch.setattr(main, "store", FailingStore())

    response = client.post("/events", json=VALID_EVENT)

    assert response.status_code >= 500
    assert response.json() != {"status": "recorded"}


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: audit write failure is logged only at DEBUG level "
    "('audit write skipped'), effectively swallowing it silently (main.py:34)",
)
def test_post_event_store_failure_is_logged_at_error_level(client, monkeypatch, caplog):
    monkeypatch.setattr(main, "store", FailingStore())

    with caplog.at_level(logging.DEBUG, logger="audit-log"):
        client.post("/events", json=VALID_EVENT)

    failure_records = [r for r in caplog.records if r.exc_info]
    assert failure_records, "no exception logged for the failed audit write"
    assert all(r.levelno >= logging.ERROR for r in failure_records)


def test_post_event_store_failure_does_not_propagate_as_500_crash(client, monkeypatch, caplog):
    """Documents current behavior: the exception is caught and logged at DEBUG."""
    monkeypatch.setattr(main, "store", FailingStore())

    with caplog.at_level(logging.DEBUG, logger="audit-log"):
        response = client.post("/events", json=VALID_EVENT)

    assert response.status_code == 201
    assert any("audit write skipped" in r.getMessage() for r in caplog.records)


# --- GET /events -------------------------------------------------------------


def test_get_events_empty_returns_empty_list(client):
    response = client.get("/events")

    assert response.status_code == 200
    assert response.json() == []


def test_get_events_returns_newest_first_with_full_records(client, fixed_clock):
    client.post("/events", json={**VALID_EVENT, "details": "first"})
    client.post("/events", json={**VALID_EVENT, "details": "second"})

    body = client.get("/events").json()

    assert body == [
        {"id": 2, "timestamp": fixed_clock.isoformat(), **VALID_EVENT, "details": "second"},
        {"id": 1, "timestamp": fixed_clock.isoformat(), **VALID_EVENT, "details": "first"},
    ]


def test_get_events_default_limit_is_100(client):
    for _ in range(101):
        client.post("/events", json=VALID_EVENT)

    body = client.get("/events").json()

    assert len(body) == 100
    assert body[0]["id"] == 101 and body[-1]["id"] == 2


def test_get_events_limit_query_param_caps_results(client):
    for _ in range(3):
        client.post("/events", json=VALID_EVENT)

    body = client.get("/events", params={"limit": 2}).json()

    assert [e["id"] for e in body] == [3, 2]


def test_get_events_limit_one_returns_latest(client):
    client.post("/events", json=VALID_EVENT)
    client.post("/events", json={**VALID_EVENT, "details": "latest"})

    body = client.get("/events", params={"limit": 1}).json()

    assert [e["details"] for e in body] == ["latest"]


def test_get_events_limit_above_count_returns_all(client):
    client.post("/events", json=VALID_EVENT)

    assert len(client.get("/events", params={"limit": 500}).json()) == 1


def test_get_events_non_integer_limit_returns_422(client):
    response = client.get("/events", params={"limit": "ten"})

    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["query", "limit"]


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: GET /events?limit=0 returns every event instead of none "
    "(store.py:42 slice [-0:])",
)
def test_get_events_limit_zero_returns_empty(client):
    for _ in range(3):
        client.post("/events", json=VALID_EVENT)

    assert client.get("/events", params={"limit": 0}).json() == []


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: GET /events accepts a negative limit and returns a "
    "truncated slice instead of 422 (main.py:39 has no ge=0 constraint)",
)
def test_get_events_negative_limit_returns_422(client):
    for _ in range(3):
        client.post("/events", json=VALID_EVENT)

    assert client.get("/events", params={"limit": -1}).status_code == 422


# --- App configuration -------------------------------------------------------


def test_app_title_is_audit_log():
    assert main.app.title == "audit-log"


def test_cors_allows_configured_web_origin(client):
    response = client.options(
        "/events",
        headers={
            "Origin": "http://localhost:3000",
            "Access-Control-Request-Method": "POST",
        },
    )

    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == "http://localhost:3000"


def test_cors_rejects_unknown_origin(client):
    response = client.options(
        "/events",
        headers={
            "Origin": "http://evil.example",
            "Access-Control-Request-Method": "POST",
        },
    )

    assert "access-control-allow-origin" not in response.headers


def test_cors_rejects_delete_method(client):
    response = client.options(
        "/events",
        headers={
            "Origin": "http://localhost:3000",
            "Access-Control-Request-Method": "DELETE",
        },
    )

    assert response.status_code == 400


def test_unknown_route_returns_404(client):
    assert client.get("/nope").status_code == 404


def test_delete_events_is_not_allowed(client):
    assert client.delete("/events").status_code == 405
