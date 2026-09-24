---
name: repo-layout-pr-conventions
description: Use when starting any task in meridian-demo, running services, creating branches, or opening pull requests.
---

# Repo Layout and PR Conventions

## Layout

| Service | Stack | Port |
|---------|-------|------|
| `services/transaction-service` | Java 17, Spring Boot, Maven | 8081 |
| `services/auth-gateway` | TypeScript, Express; also serves the web UI from `public/` | 3000 |
| `services/pii-vault` | Python 3.11, FastAPI | 8002 |
| `services/audit-log` | Python 3.11, FastAPI | 8003 |

- Run everything with `./scripts/start-all.sh`, then open http://localhost:3000 (login `jdoe` / `Meridian!2026`, MFA `246810`).
- Each service is independent — keep dependencies, tests, and config inside its own folder.
- All data is synthetic.

## Pull requests

- One service per PR.
- Branch name: `devin/<task>-<service>`.
- PR descriptions state what changed, how it was tested, and anything needing a human decision.
- CI must pass before requesting review.
- Never merge your own PR.
