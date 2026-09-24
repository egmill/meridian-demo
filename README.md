# Meridian Platform

Backend services and web client for Meridian's retail online banking.

## Architecture

```
                 ┌────────────────────────────┐
  browser ──────▶│ auth-gateway  :3000        │  sign-in, MFA, session tokens,
                 │ (TypeScript / Express)     │  serves the web UI (public/)
                 └────────────────────────────┘
      │
      ├─────────▶ transaction-service :8081 (Java 17 / Spring Boot)
      │              accounts, balances, transfers
      │                  │
      │                  └──▶ audit-log :8003   (writes a TRANSFER event
      │                                         before balances change)
      │
      ├─────────▶ pii-vault :8002 (Python / FastAPI)
      │              customer profiles, SSN masking
      │
      └─────────▶ audit-log :8003 (Python / FastAPI)
                     append-only audit events
```

| Service | Stack | Port | Tests |
|---|---|---|---|
| `services/transaction-service` | Java 17, Spring Boot 3, Maven | 8081 | JUnit 5 (`mvn test`) |
| `services/auth-gateway` | TypeScript, Node 20, Express | 3000 | Jest (`npm test`) |
| `services/pii-vault` | Python 3.11, FastAPI | 8002 | none |
| `services/audit-log` | Python 3.11, FastAPI | 8003 | none |

All data is in memory and seeded with **synthetic** demo records on startup.

## Running locally

Prerequisites: JDK 17+, Maven 3.9+, Node 20+, Python 3.11+.

```bash
./scripts/start-all.sh
```

Then open http://localhost:3000. Demo login: `jdoe` / `Meridian!2026`, MFA code `246810`.

To run a single service:

```bash
# transaction-service
cd services/transaction-service && mvn spring-boot:run

# auth-gateway (also serves the UI)
cd services/auth-gateway && npm install && npm start

# pii-vault / audit-log
cd services/pii-vault && pip install -r requirements.txt && uvicorn app.main:app --port 8002
cd services/audit-log && pip install -r requirements.txt && uvicorn app.main:app --port 8003
```

## Tests and coverage

```bash
# Java: unit tests + JaCoCo report (target/site/jacoco/index.html)
cd services/transaction-service && mvn test

# Java: mutation testing with PIT (target/pit-reports/index.html)
cd services/transaction-service && mvn test org.pitest:pitest-maven:mutationCoverage

# TypeScript
cd services/auth-gateway && npm test -- --coverage
```

CI (`.github/workflows/ci.yml`, mirrored in `Jenkinsfile`) currently runs the Java tests only.
