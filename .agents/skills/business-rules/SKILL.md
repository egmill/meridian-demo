---
name: business-rules
description: Use when writing tests for, reviewing, or reasoning about the correctness of transfers, authentication, SSN/PII handling, or audit logging in meridian-demo.
---

# Business Rules

## Money

- All monetary amounts use `BigDecimal` (Java) or `Decimal` (Python) — never `float` / `double`.
- Transfer amounts must be greater than zero.
- Round to 2 decimal places using banker's rounding (`HALF_EVEN`).

## Authentication

- A token is invalid at or after its expiry timestamp.
- After 5 consecutive failed MFA attempts, the account locks for 15 minutes.
- The failure counter resets after a successful login and when the lockout window ends.

## PII

- SSNs are displayed only as the last 4 digits (`***-**-1234`).
- Any input shorter than 4 digits, malformed, or in an unexpected format must be fully masked.
- Masking must never crash and must never return the raw value.

## Audit

- Every state-changing operation (transfer, login, PII access) writes an audit record.
- If the audit write fails, the operation must fail and return an error.
- Audit failures must never be swallowed silently.
