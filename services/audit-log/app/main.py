import logging
import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from .store import AuditStore

logger = logging.getLogger("audit-log")

app = FastAPI(title="audit-log")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[os.getenv("WEB_ORIGIN", "http://localhost:3000")],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

store = AuditStore()


class AuditEventIn(BaseModel):
    actor: str
    action: str
    details: str


@app.post("/events", status_code=201)
def write_event(event: AuditEventIn) -> dict:
    try:
        store.append(event.actor, event.action, event.details)
    except Exception:
        logger.debug("audit write skipped", exc_info=True)
    return {"status": "recorded"}


@app.get("/events")
def list_events(limit: int = 100) -> list[dict]:
    return store.list(limit)
