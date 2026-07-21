# Session: Apply the comparison base to Maven and Gradle selection

- **intent:** Apply the comparison base to Maven and Gradle selection
- **started:** 2026-07-21

## Decision: Use the merge base for both SELECT inputs

- **where:** `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/diff/CurrentChangesResolver.java; blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/mojo/SelectMojo.java; blastradius-gradle-plugin/src/main/java/io/github/baokhang83/blastradius/gradle/BlastradiusPlugin.java`
- **why:** The same comparison-base SHA keys the persisted index and bounds the diff to HEAD, so target-only commits after a branch diverges cannot affect PR selection.
- **alternative:** Use the resolved target tip as the index and diff baseline — rejected because it mixes target-only changes into the PR comparison.
- **design:** ../design.md#sequence-select-on-a-pr-branch
- **constitution:** §III, §IV, §V
- **trust:** ✓ verified

## Decision: Leave the task unfiltered when no common ancestor exists

- **where:** `blastradius-gradle-plugin/src/main/java/io/github/baokhang83/blastradius/gradle/GitBuildState.java; blastradius-gradle-plugin/src/main/java/io/github/baokhang83/blastradius/gradle/NoComparisonBaseFallbackAction.java; blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/index/IndexApplicability.java`
- **why:** An absent merge base has no sound index key or diff endpoint, so both adapters surface a named safe fallback instead of guessing; Gradle models the nullable value in configuration-cache state.
- **alternative:** Invent an index key, compare with the target tip, or fail the test task — rejected because those options can misselect tests or turn an uncertain comparison into a build failure.
- **design:** ../design.md#sequence-select-on-a-pr-branch
- **constitution:** §III, §V
- **trust:** ✓ verified

## Knowledge transfer

- **Maven comparison state:** `CurrentChanges` keeps the configured ref's resolved tip distinct
  from its optional merge base with `HEAD`. Only a present comparison base is used to locate an
  index and classify changes; otherwise `MERGE_BASE_UNAVAILABLE` reports the conservative
  fallback. **Status:** verified by the real Maven diverged-branch subprocess test.
- **Gradle configuration-cache state:** `GitBuildState` uses a nullable comparison-base string
  because the state is serialized by Gradle. A present value becomes both a task input and the
  key for `ApplySelectionAction`; no value installs an action that leaves the normal full test
  task unfiltered. **Status:** verified by the Gradle functional diverged-branch test.
