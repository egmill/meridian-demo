"""In-memory customer PII store. Seeded with synthetic demo customers."""

from dataclasses import dataclass
from itertools import count
from threading import Lock


@dataclass
class Customer:
    id: str
    name: str
    email: str
    ssn: str


class CustomerStore:
    def __init__(self) -> None:
        self._customers: dict[str, Customer] = {}
        self._ids = count(1004)
        self._lock = Lock()
        # Synthetic data only.
        for customer in (
            Customer("C-1001", "Avery Chen", "avery.chen@example.com", "123-45-6789"),
            Customer("C-1002", "Marcus Webb", "marcus.webb@example.com", "987-65-4321"),
            Customer("C-1003", "Priya Natarajan", "priya.n@example.com", "987-65-4322"),
        ):
            self._customers[customer.id] = customer

    def list(self) -> list[Customer]:
        return sorted(self._customers.values(), key=lambda c: c.id)

    def get(self, customer_id: str) -> Customer | None:
        return self._customers.get(customer_id)

    def create(self, name: str, email: str, ssn: str) -> Customer:
        with self._lock:
            customer = Customer(f"C-{next(self._ids)}", name, email, ssn)
            self._customers[customer.id] = customer
        return customer
