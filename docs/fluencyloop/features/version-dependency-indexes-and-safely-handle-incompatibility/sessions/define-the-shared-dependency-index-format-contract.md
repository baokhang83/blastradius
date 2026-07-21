# Session: Define the shared dependency-index format contract

- **intent:** Define the shared dependency-index format contract
- **started:** 2026-07-21

## Decision: Migrate only the known unversioned index schema

- **where:** `blastradius-core/src/main/java/io/github/baokhang83/blastradius/core/index/DependencyIndexFormat.java`
- **why:** Maven and Gradle share one persisted schema, so one core contract maps only legacy version 0 to the current version and leaves every other value visible for a safe adapter fallback.
- **alternative:** Treat all unknown versions as current or fail the build on read — rejected because the former can misread data and the latter blocks an otherwise safe full test run.
- **design:** ../design.md#class-diagram
- **constitution:** §II, §III, §IV
- **trust:** ✓ verified

## Knowledge transfer

- **DependencyIndexFormat:** defines the version written by new indexes, upgrades only the
  known missing-version legacy schema, and leaves unsupported values unchanged so an adapter can
  reject them explicitly. **Status:** verified by focused core tests.
