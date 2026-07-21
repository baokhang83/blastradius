# Session: Apply format checks in Maven and Gradle selection

- **intent:** Apply format checks in Maven and Gradle selection
- **started:** 2026-07-21

## Decision: Reject unsupported index formats before selection

- **where:** `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/index/IndexApplicabilityResolver.java; blastradius-gradle-plugin/src/main/java/io/github/baokhang83/blastradius/gradle/IndexApplicabilityResolver.java`
- **why:** Both adapters check the shared schema version before Git or selection work, allowing the known legacy schema and routing every unsupported version through a named full-test fallback.
- **alternative:** Let Jackson fail generically or try to select with unknown data — rejected because users lose the actionable reason or risk an unsound test filter.
- **design:** ../design.md#sequence-read-an-index-safely
- **constitution:** §III, §IV, §V
- **trust:** ✓ verified

## Knowledge transfer

- **DependencyIndex models:** Maven and Gradle emit `formatVersion: 1` for newly tracked
  indexes, normalize Jackson's missing primitive value (`0`) for the known legacy schema, and
  retain unfamiliar values so they can be rejected rather than disguised as current. **Status:**
  verified by Maven index I/O and Gradle functional tests.
- **IndexApplicability resolvers:** evaluate schema compatibility before commit reachability and
  anchor matching. `FORMAT_VERSION_MISMATCH` carries no usable index, causing the existing
  full-test fallback path; Maven and Gradle both surface that reason. **Status:** verified by
  focused Maven and Gradle tests.
