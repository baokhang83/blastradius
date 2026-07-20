# Session: Route Gradle index access through the store SPI

- **intent:** Route Gradle index access through the store SPI
- **started:** 2026-07-20

## Decision: Construct the local store inside Gradle task actions

- **where:** `blastradius-gradle-plugin/.../ApplySelectionAction.java; WriteTrackingIndexAction.java; BlastradiusPlugin.java`
- **why:** Both SELECT and TRACK receive only root and file inputs, derive the same key at execution time, and construct FileIndexStore there so the configuration cache does not capture a live storage or Jackson object.
- **alternative:** Keep Gradle-specific readers/writers or retain a store instance in task configuration — rejected because the former duplicates the SPI boundary and the latter risks configuration-cache-incompatible task state.
- **design:** ../design.md#sequence-read-or-write-an-existing-local-index
- **constitution:** §II, §III, §IV
- **trust:** ✓ verified by `./gradlew test`

## Knowledge transfer

- **Gradle SELECT action:** `ApplySelectionAction` receives the project root and resolved index
  file, derives a root-relative key at task execution, and asks the shared store before applying
  any filter. Missing or unreadable values leave the test task unfiltered. **Status:** documented.
- **Gradle TRACK action:** `WriteTrackingIndexAction` receives the same root and index file, then
  writes the completed agent records through the same derived key after the test workers finish.
  **Status:** documented.
- **Configuration cache boundary:** Gradle snapshots the configured task actions; keeping only
  `File` and string fields there and constructing `FileIndexStore` inside `execute` avoids
  retaining non-configuration state in the cache. **Status:** documented.
- **Validation:** the existing TestKit functional test covers TRACK followed by SELECT and
  configuration-cache reuse; `./gradlew test` passed locally. **Status:** verified.
