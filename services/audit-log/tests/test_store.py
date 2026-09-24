import threading

import pytest

from app.store import (
    MAX_ACTION_LENGTH,
    MAX_ACTOR_LENGTH,
    MAX_DETAILS_LENGTH,
    AuditStore,
)


def test_append_returns_event_with_all_fields(audit_store, fixed_clock):
    event = audit_store.append("user-1", "LOGIN", "mfa ok")

    assert event == {
        "id": 1,
        "timestamp": fixed_clock.isoformat(),
        "actor": "user-1",
        "action": "LOGIN",
        "details": "mfa ok",
    }


def test_append_timestamp_is_utc_iso8601(audit_store):
    event = audit_store.append("user-1", "LOGIN", "x")

    assert event["timestamp"] == "2026-01-15T12:00:00+00:00"


def test_ids_are_sequential_starting_at_one(audit_store):
    first = audit_store.append("a", "b", "c")
    second = audit_store.append("a", "b", "c")
    third = audit_store.append("a", "b", "c")

    assert [first["id"], second["id"], third["id"]] == [1, 2, 3]


def test_separate_stores_have_independent_id_sequences():
    one, two = AuditStore(), AuditStore()
    one.append("a", "b", "c")

    assert two.append("a", "b", "c")["id"] == 1


def test_append_persists_event_for_listing(audit_store):
    event = audit_store.append("user-1", "TRANSFER", "acct 1 -> 2")

    assert audit_store.list() == [event]


def test_append_accepts_empty_strings(audit_store):
    event = audit_store.append("", "", "")

    assert event["actor"] == "" and event["action"] == "" and event["details"] == ""


@pytest.mark.parametrize(
    ("field", "limit"),
    [("actor", MAX_ACTOR_LENGTH), ("action", MAX_ACTION_LENGTH), ("details", MAX_DETAILS_LENGTH)],
)
def test_append_accepts_value_exactly_at_column_width(audit_store, field, limit):
    args = {"actor": "a", "action": "b", "details": "c"}
    args[field] = "x" * limit

    event = audit_store.append(**args)

    assert event[field] == "x" * limit


def test_append_rejects_actor_over_column_width(audit_store):
    with pytest.raises(ValueError, match="actor exceeds column width"):
        audit_store.append("x" * (MAX_ACTOR_LENGTH + 1), "b", "c")


def test_append_rejects_action_over_column_width(audit_store):
    with pytest.raises(ValueError, match="action exceeds column width"):
        audit_store.append("a", "x" * (MAX_ACTION_LENGTH + 1), "c")


def test_append_rejects_details_over_column_width(audit_store):
    with pytest.raises(ValueError, match="details exceeds column width"):
        audit_store.append("a", "b", "x" * (MAX_DETAILS_LENGTH + 1))


def test_rejected_append_stores_nothing_and_consumes_no_id(audit_store):
    with pytest.raises(ValueError):
        audit_store.append("x" * (MAX_ACTOR_LENGTH + 1), "b", "c")

    assert audit_store.list() == []
    assert audit_store.append("a", "b", "c")["id"] == 1


def test_column_width_constants_match_production_table():
    assert (MAX_ACTOR_LENGTH, MAX_ACTION_LENGTH, MAX_DETAILS_LENGTH) == (64, 32, 128)


def test_list_empty_store_returns_empty_list(audit_store):
    assert audit_store.list() == []


def test_list_returns_newest_first(audit_store):
    for i in range(3):
        audit_store.append("a", "b", str(i))

    assert [e["id"] for e in audit_store.list()] == [3, 2, 1]


def test_list_default_limit_is_100(audit_store):
    for _ in range(105):
        audit_store.append("a", "b", "c")

    ids = [e["id"] for e in audit_store.list()]

    assert len(ids) == 100
    assert ids[0] == 105 and ids[-1] == 6


def test_list_limit_returns_only_newest_n(audit_store):
    for _ in range(5):
        audit_store.append("a", "b", "c")

    assert [e["id"] for e in audit_store.list(limit=2)] == [5, 4]


def test_list_limit_one_returns_only_latest(audit_store):
    audit_store.append("a", "b", "c")
    audit_store.append("a", "b", "d")

    assert [e["details"] for e in audit_store.list(limit=1)] == ["d"]


def test_list_limit_larger_than_store_returns_everything(audit_store):
    audit_store.append("a", "b", "c")

    assert len(audit_store.list(limit=1000)) == 1


def test_list_limit_equal_to_size_returns_everything(audit_store):
    for _ in range(3):
        audit_store.append("a", "b", "c")

    assert len(audit_store.list(limit=3)) == 3


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: list(limit=0) returns every event instead of none "
    "(store.py:42 slices _events[-0:], which is the whole list)",
)
def test_list_limit_zero_returns_no_events(audit_store):
    for _ in range(3):
        audit_store.append("a", "b", "c")

    assert audit_store.list(limit=0) == []


@pytest.mark.xfail(
    strict=True,
    reason="SUSPECTED BUG: negative limit is not rejected; list(limit=-1) silently "
    "drops the oldest event and returns the rest (store.py:42)",
)
def test_list_negative_limit_is_rejected(audit_store):
    for _ in range(3):
        audit_store.append("a", "b", "c")

    with pytest.raises(ValueError):
        audit_store.list(limit=-1)


def test_list_returns_copy_not_internal_storage(audit_store):
    audit_store.append("a", "b", "c")
    listed = audit_store.list()
    listed.clear()

    assert len(audit_store.list()) == 1


def test_concurrent_appends_assign_unique_ids_and_lose_nothing(audit_store):
    per_thread = 50
    threads = [
        threading.Thread(
            target=lambda: [audit_store.append("a", "b", "c") for _ in range(per_thread)]
        )
        for _ in range(8)
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    events = audit_store.list(limit=10_000)
    ids = [e["id"] for e in events]

    assert len(events) == 8 * per_thread
    assert len(set(ids)) == len(ids)
