# Session: Compose explicit test exclusions into every target Maven build

- **intent:** Compose explicit test exclusions into every target Maven build
- **started:** 2026-08-02

## Decision: Compose exact class exclusions into every Maven selector

- **where:** `validator/build/SkippedTests and MavenBuildRunner`
- **why:** One normalized exclusion list reaches full builds and confirmation reruns, so known flakes never execute or contaminate the observed ground truth.
- **alternative:** Filter reports after Maven completes — rejected: the flaky test would still run, fail, hang, or alter shared state.
- **design:** ../design.md#selector-contract
- **constitution:** §III, §V
- **trust:** ✓ verified

## Knowledge transfer

- **SkippedTests:** parses repeated comma-separated CLI values into ordered, distinct fully qualified test classes and rejects blank or wildcard entries, status: documented.
- **MavenBuildRunner:** adds negative Surefire patterns to both full-suite and single-test selectors, and enables no-specified-tests safeguards whenever exclusions create a selector, status: documented.
- **Evidence boundary:** excluded classes do not execute and therefore contribute neither dependency records nor outcomes; both JSON and text reports now state that boundary explicitly, status: documented.
