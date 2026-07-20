# Session: Verify JDK matrix coverage

- **intent:** Verify JDK matrix coverage
- **started:** 2026-07-20

## Decision: Use one independent JDK matrix job

- **where:** `.github/workflows/build.yml verify job`
- **why:** A single parameterized job guarantees both JDKs execute the same checkout, Maven setup, cache, and full verification lifecycle; fail-fast disabled preserves both results.
- **alternative:** Two copied JDK-specific jobs — rejected because their setup and verification steps could drift.
- **design:** ../design.md#compatibility-boundary
- **constitution:** §VI
- **trust:** ⚠ not independently verified

## Knowledge transfer

- The `verify` job is expanded once for each value in `matrix.java`; each job receives the
  selected Temurin version through `actions/setup-java` and runs the same Maven reactor command.
  Status: documented.
- `fail-fast: false` lets both matrix entries finish after a sibling failure, preserving the
  complete compatibility evidence for review. GitHub Actions remains the final workflow-parser
  and runtime validation. Status: follow-up.
