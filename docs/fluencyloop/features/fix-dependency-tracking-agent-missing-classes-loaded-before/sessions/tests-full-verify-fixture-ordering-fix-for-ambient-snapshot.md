# Session: tests + full verify: fixture ordering fix for ambient-snapshot timing regression

- **intent:** tests + full verify: fixture ordering fix for ambient-snapshot timing regression
- **started:** 2026-07-26

<!--
FluencyLoop Stage 3 — a session is a slice of the build. One block per meaningful decision,
appended at the slice boundary as it's taught. No `commits:` field: the feature is a branch,
so the PR view derives commits live from git.

Each decision is a `## Decision:` heading followed by a bullet list — one bullet per field, so
it renders one-per-line as real Markdown (plain `key: value` lines collapse into a single
paragraph when rendered). Fields:

  where        — file/area the decision lives in (NOT a line number — survives refactoring)
  why          — the rationale, taught live before it was written
  alternative  — the rejected option and why (this is what makes it rationale, not description)
  design       — (optional) ../design.md#anchor — the diagram this decision shaped or used
  constitution — (optional) §N — the principle this decision serves or trades off against
  trust        — ✓ verified | ⚠ not independently verified  (about the DECISION, never the person)

Delete this comment and the example below once real decisions land.
-->

---

## Decision: fixed 2 integration tests by adding a deterministic warm-up test class, not by weakening the ambient fallback

- **where:** `blastradius-validator/src/test/java/io/github/baokhang83/blastradius/validator/EndToEndVerdictIntegrationTest.java`
- **why:** the full reactor test run surfaced that the ambient snapshot (taken on the first executionStarted in the fork) incidentally captured Shared when GapTest/GapATest happened to be the very first test class run, since their own @BeforeAll runs before that first test's window opens — this coincidentally 'fixed' the exact narrow gap these tests exist to document, flipping verdict from FAIL to PASS. The fixtures needed a preceding warm-up test class (plus explicit alphabetical runOrder in FixtureProjectBuilder's pom, so cross-class order is deterministic rather than filesystem-dependent) so the ambient snapshot fires before the Gap classes' own @BeforeAll, preserving the real remaining limitation: a class loaded only during a later class's @BeforeAll is still invisible to tracking
- **alternative:** narrow AmbientDependencySelector or special-case @BeforeAll-loaded classes so this fixture keeps failing — rejected: that would reintroduce exactly the bug this whole feature fixes for any project where the first-loaded test class happens to load its dependency in @BeforeAll; the fixture was wrong to assume that shape, not the fix
- **trust:** ⚠ not independently verified

## Knowledge transfer

- **`FixtureProjectBuilder`'s base pom** now sets `<runOrder>alphabetical</runOrder>` on surefire — every fixture-based test that depends on cross-class execution order (not just these two) is now deterministic instead of relying on filesystem enumeration order. status: documented
- **`DependencyIndexFormatTest`** had a second stale assertion from Task #2's work (`isCurrentVersion` should be false for the pre-ambient version) — added alongside the corrected migration-target test, both now passing. status: documented
- **Remaining real gap, now precisely scoped**: a class loaded only inside a test class's `@BeforeAll`, where that class is *not* the first test executed in the fork, is still invisible to both per-test tracking and the ambient snapshot — this is the one case the ambient-dependency fix does not close, and it's what `EndToEndVerdictIntegrationTest`/`MultiWouldMissIntegrationTest` now correctly continue to document. status: documented

## Decision: added clean to the plugin's E2E install command, not a target-dir workaround

- **where:** `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/EndToEndTestSupport.java`
- **why:** mvn install (no clean) can leave a stale shaded uber-jar in place when the plugin's own sources haven't changed, since maven-jar-plugin's up-to-date check skips regenerating the plain jar; shade then bootstraps its assembly from that self-referential stale jar and discards the freshly-rebuilt blastradius-core classes as duplicates (first-seen-wins) - so a correct, unit-tested source fix could still silently not take effect through the actually-installed plugin. Confirmed via javap -c decompilation of the embedded class before/after: 0 to 1 occurrences of the fix's Set.remove call
- **alternative:** manually delete blastradius-maven-plugin/target before each E2E run, or touch sources to defeat jar-plugin's up-to-date check - rejected: both are fragile hand-rolled workarounds for exactly what clean does correctly as maven's own designed mechanism
- **trust:** ✓ verified

## Decision: covered the ambient-snapshot mechanism's unit gap with the safe no-agent path only, not real capture

- **where:** `blastradius-core/src/test/java/io/github/baokhang83/blastradius/core/tracking/DependencyTrackingAgentTest.java`
- **why:** loadedClasses() reads a private static instrumentation field only ever set by premain, and that field plus installedAgent are shared JVM-wide across every test class in a surefire fork (default reuseForks=true) - faking a real Instrumentation via reflection to exercise actual capture would leak global state into every other test in the module, for behavior the E2E suite (IndexReuseAcrossBuildsTest, via a real -javaagent attach) already verifies authentically end to end
- **alternative:** reflectively inject a fake Instrumentation and call premain from the unit test to exercise real class-capture - rejected: pollutes shared static state across the whole test JVM fork for coverage the E2E path already provides
- **trust:** ⚠ not independently verified

## Knowledge transfer

- **`AmbientDependencySelectorTest`** (new file) covers `AmbientDependencySelector.shouldFallback`'s `Collections.disjoint` logic directly (trigger/no-trigger across an ambient hit, a miss, an empty ambient set, and no changed classes) plus `select()`'s `FALLBACK_AMBIENT_DEPENDENCY` reason — this class had zero prior test coverage. status: documented
- **`DependencyTrackingAgentTest`** gained three tests for the ambient-snapshot surface (`ambientDependencies()` defaults empty with no agent attached, `snapshotAmbientDependencies()` is a safe idempotent no-op without a real `Instrumentation`, and `ambientDependencies()` returns an immutable `Set.copyOf`) — the actual class-capturing behavior (`loadedClasses()` reading a real `Instrumentation`) is only exercised for real by the E2E suite, since no unit-test seam exists to inject one without touching shared static state (see decision above). status: documented
- **`DependencyIndexFormatTest`** already had full coverage of the version-2 (`ambientDependencies`) migration path (`migrateLegacyVersion`, `isCurrentVersion` for both the pre-ambient and current versions) from earlier in this feature — checked, nothing further needed. status: documented
- **`TestBoundaryListenerTest`** was left as-is: it already exercises `executionStarted`'s no-installed-agent path (via `DependencyTrackingAgent.recordAmbientSnapshot()`'s null-safe no-op) implicitly, since the existing test would fail if that call misbehaved; asserting the snapshot-before-recordTestStarted ordering more directly would need the same static-state injection ruled out above. status: follow-up
