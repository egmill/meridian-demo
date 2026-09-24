---
name: testing-standards
description: Use when writing, running, or reviewing tests, coverage, or CI configuration for any meridian-demo service.
---

# Testing Standards

## Frameworks and commands

### transaction-service (Java)

- Frameworks: JUnit 5 + Mockito
- Run tests + coverage: `mvn test` (JaCoCo report at `target/site/jacoco/index.html`)
- Mutation testing: `mvn test org.pitest:pitest-maven:mutationCoverage` (report at `target/pit-reports/index.html`)

### auth-gateway (TypeScript)

- Frameworks: Jest + supertest
- Run tests + coverage: `npm test -- --coverage`

### pii-vault and audit-log (Python)

- Frameworks: pytest + pytest-cov + FastAPI `TestClient`
- Run tests + coverage: `pytest --cov=app`
- Test-only dependencies go in `requirements-dev.txt`, never in `requirements.txt`.

## Conventions

- Test files mirror source paths.
- Test names describe behavior.
- One behavior per test.
- Mock all network calls and calls to other services.
- Tests are deterministic; use an injected or fixed clock rather than the system time.
- Use synthetic test data only — never realistic SSNs, account numbers, or customer details.

## Suspected bugs

If code contradicts the business rules:

1. Write the test for the **correct** behavior.
2. Mark it expected-to-fail:
   - JUnit: `@Disabled`
   - Jest: `test.failing`
   - pytest: `@pytest.mark.xfail`
   with the reason `"SUSPECTED BUG: <description>"`.
3. List it in the PR description.

Never encode incorrect behavior to make a test pass.
