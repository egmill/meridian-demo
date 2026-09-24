"""Tests for app/store.py. All customer data is synthetic."""

from concurrent.futures import ThreadPoolExecutor

from app.store import Customer, CustomerStore


def test_store_is_seeded_with_three_synthetic_customers():
    store = CustomerStore()
    assert [c.id for c in store.list()] == ["C-1001", "C-1002", "C-1003"]


def test_list_returns_customers_sorted_by_id():
    store = CustomerStore()
    store.create("Zed Test", "zed@example.com", "000-00-0001")
    ids = [c.id for c in store.list()]
    assert ids == sorted(ids)
    assert ids[-1] == "C-1004"


def test_list_returns_customer_instances():
    store = CustomerStore()
    assert all(isinstance(c, Customer) for c in store.list())


def test_get_returns_existing_customer():
    store = CustomerStore()
    customer = store.get("C-1001")
    assert customer is not None
    assert customer.id == "C-1001"
    assert customer.name == "Avery Chen"
    assert customer.email == "avery.chen@example.com"


def test_get_returns_none_for_unknown_id():
    assert CustomerStore().get("C-9999") is None


def test_get_returns_none_for_empty_id():
    assert CustomerStore().get("") is None


def test_get_is_case_sensitive():
    assert CustomerStore().get("c-1001") is None


def test_create_assigns_next_sequential_id_after_seed():
    store = CustomerStore()
    created = store.create("Test User", "test@example.com", "111-22-3333")
    assert created.id == "C-1004"


def test_create_assigns_unique_incrementing_ids():
    store = CustomerStore()
    first = store.create("A", "a@example.com", "111-22-3333")
    second = store.create("B", "b@example.com", "111-22-4444")
    assert first.id == "C-1004"
    assert second.id == "C-1005"


def test_create_stores_all_fields_verbatim():
    store = CustomerStore()
    created = store.create("Test User", "test@example.com", "111-22-3333")
    assert created.name == "Test User"
    assert created.email == "test@example.com"
    assert created.ssn == "111-22-3333"


def test_created_customer_is_retrievable():
    store = CustomerStore()
    created = store.create("Test User", "test@example.com", "111-22-3333")
    assert store.get(created.id) is created


def test_create_increases_list_length():
    store = CustomerStore()
    before = len(store.list())
    store.create("Test User", "test@example.com", "111-22-3333")
    assert len(store.list()) == before + 1


def test_separate_store_instances_are_isolated():
    a = CustomerStore()
    b = CustomerStore()
    a.create("Only In A", "a@example.com", "111-22-3333")
    assert b.get("C-1004") is None
    assert len(b.list()) == 3


def test_concurrent_creates_produce_unique_ids():
    store = CustomerStore()
    with ThreadPoolExecutor(max_workers=8) as pool:
        created = list(
            pool.map(
                lambda i: store.create(f"User {i}", f"u{i}@example.com", "111-22-3333"),
                range(200),
            )
        )
    ids = [c.id for c in created]
    assert len(set(ids)) == 200
    assert len(store.list()) == 203
