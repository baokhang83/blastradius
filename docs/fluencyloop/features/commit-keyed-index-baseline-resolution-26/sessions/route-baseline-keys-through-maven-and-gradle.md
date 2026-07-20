# Session: Route baseline keys through Maven and Gradle

- **intent:** Route baseline keys through Maven and Gradle
- **started:** 2026-07-20

## Decision: Resolve exactly the index recorded for the diff baseline

- **where:** `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/mojo/SelectMojo.java; blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/index/IndexApplicabilityResolver.java; blastradius-gradle-plugin/src/main/java/io/github/baokhang83/blastradius/gradle/ApplySelectionAction.java; blastradius-gradle-plugin/src/main/java/io/github/baokhang83/blastradius/gradle/BlastradiusPlugin.java`
- **why:** SELECT derives the commit-keyed path from its resolved base commit and validates the loaded index anchor against that commit, so a stale index cannot select an incorrect subset. Gradle supplies a filtered input collection so an absent index reaches the intentional safe fallback while a present index remains configuration-cache tracked.
- **alternative:** Read the legacy flat path or scan indexes for any reachable anchor — rejected because neither proves the data belongs to this diff baseline.
- **design:** ../design.md#sequence-store-on-baseline-select-on-a-branch
- **constitution:** §III, §IV, §V
- **trust:** ✓ verified

## Knowledge transfer

- **Baseline-index lookup:** both integrations derive the storage key from the diff's resolved
  base commit, then require the loaded index's anchor to match it. A missing, unreachable, or
  mismatched index deliberately produces the existing safe full-test fallback. **Status:**
  verified by the direct resolver tests and integration tests.
- **Gradle task input:** a filtered `FileCollection` is empty when the expected baseline key is
  absent and contains that exact file when present. This avoids Gradle failing validation for a
  declared-but-missing file while preserving configuration-cache tracking of real indexes.
  **Status:** verified by the configuration-cache TestKit scenario.
