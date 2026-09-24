---
name: compliance-scope
description: Use when writing tests, reviewing code, or assessing test coverage in meridian-demo, or when a task mentions compliance, OCC, audit, or critical paths.
---

# Compliance Scope

Meridian is preparing for an OCC examination. Work on the in-scope code paths below is subject to the requirements in this skill.

## In-scope code paths

| Area | Location | Stack |
|------|----------|-------|
| Transaction processing | `services/transaction-service` | Java / Spring Boot |
| Authentication | `services/auth-gateway` | TypeScript / Express |
| PII handling | `services/pii-vault` | Python / FastAPI |
| Audit logging | `services/audit-log` | Python / FastAPI |

## Requirements

- Every public endpoint and service method has unit tests covering:
  - the happy path
  - input validation failures
  - error handling
  - boundary values
- At least 80% line coverage per service.
- A PIT mutation score of at least 70% for `transaction-service`.
- Every service has a CI job that runs its tests on each PR, in both `.github/workflows/ci.yml` and the `Jenkinsfile`.

## Compliance findings

Anything that could:

- move money incorrectly,
- allow unauthorized access,
- expose PII, or
- lose an audit record

is a potential compliance finding and must be called out explicitly in your review, test notes, or PR description.
