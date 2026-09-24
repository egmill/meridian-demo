import os

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from .masking import mask_ssn, ssn_last4
from .store import Customer, CustomerStore

app = FastAPI(title="pii-vault")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[os.getenv("WEB_ORIGIN", "http://localhost:3000")],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

store = CustomerStore()


class CustomerIn(BaseModel):
    name: str
    email: str
    ssn: str


def to_profile(customer: Customer) -> dict:
    return {
        "id": customer.id,
        "name": customer.name,
        "email": customer.email,
        "ssnMasked": mask_ssn(customer.ssn),
        "ssnLast4": ssn_last4(customer.ssn),
    }


@app.get("/customers")
def list_customers() -> list[dict]:
    return [{"id": c.id, "name": c.name} for c in store.list()]


@app.get("/customers/{customer_id}")
def get_customer(customer_id: str) -> dict:
    customer = store.get(customer_id)
    if customer is None:
        raise HTTPException(status_code=404, detail="Customer not found")
    return to_profile(customer)


@app.post("/customers", status_code=201)
def create_customer(body: CustomerIn) -> dict:
    return to_profile(store.create(body.name, body.email, body.ssn))
