# Session: Wire Kotlin candidates through every selection entry point

- **intent:** Wire Kotlin candidates through every selection entry point
- **started:** 2026-07-20

## Knowledge transfer

- **Selection entry points** — the core engine, Maven adapter, Gradle adapter, and validator
  independently derive the changed-class set before applying the selection rules. Each expands a
  `ChangedFile` through `candidateClassNames()` so Kotlin file facades have the same semantics
  everywhere. **Status:** documented.
- **Maven fixture setup** — the test fixture enables `kotlin-maven-plugin` with Maven extensions,
  adds Kotlin standard library at runtime, and writes source under the conventional Kotlin roots.
  This makes the end-to-end test compile and discover real Kotlin JUnit classes rather than
  simulating their bytecode. **Status:** documented.

## Decision: Expand Kotlin candidates before every selection entry point

- **where:** `core engine, Maven and Gradle adapters, and validator`
- **why:** Each entry point determines modified tests before invoking the shared engine, so all must see the same file facade and source-root candidates for consistent selection.
- **alternative:** Expand candidates only inside the core selection engine — rejected: Maven, Gradle, and the validator each derive new-or-modified tests first and would retain divergent behavior.
- **design:** ../design.md#design
- **constitution:** §IV
- **trust:** ✓ verified
