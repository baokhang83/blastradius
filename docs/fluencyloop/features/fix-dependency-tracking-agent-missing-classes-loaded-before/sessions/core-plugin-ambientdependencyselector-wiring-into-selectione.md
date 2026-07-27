# Session: core+plugin: AmbientDependencySelector wiring into SelectionEngine, SelectMojo, ConsoleSummaryRenderer

- **intent:** core+plugin: AmbientDependencySelector wiring into SelectionEngine, SelectMojo, ConsoleSummaryRenderer
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

## Decision: distinct FALLBACK_AMBIENT_DEPENDENCY reason instead of reusing FALLBACK_NON_SOURCE_CHANGE

- **where:** `blastradius-core/src/main/java/io/github/baokhang83/blastradius/core/selection/SelectionReason.java`
- **why:** a changed class showing up in the ambient set is a fundamentally different signal from a non-source file change — collapsing them into one reason would hide from the console/report exactly why every test ran, which is the whole point of Principle V (Explainability)
- **alternative:** reuse FALLBACK_NON_SOURCE_CHANGE for both cases — rejected: it would silently blur two distinct root causes in the build report, making the ambient-dependency bug invisible to anyone reading the summary
- **constitution:** Principle V (Explainability)
- **trust:** ⚠ not independently verified

## Decision: wire real ambient data into SelectMojo/RunCommand, stub Set.of() in ApplySelectionAction

- **where:** `blastradius-gradle-plugin/src/main/java/io/github/baokhang83/blastradius/gradle/ApplySelectionAction.java`
- **why:** the gradle-plugin's tracking pipeline doesn't capture ambient dependencies yet — passing Set.of() is an honest default for what it actually has, rather than pretending it participates in this fix
- **alternative:** wire the gradle-plugin's tracking side up to capture ambient data too, in this same slice — rejected: out of scope for this bugfix, and would silently widen the blast radius of a targeted fix into an unrelated module
- **trust:** ⚠ not independently verified

## Knowledge transfer

- **`AmbientDependencySelector`** (new, `core/selection`) — wraps `Collections.disjoint(changedClassNames, ambientDependencies)` to decide whether *any* changed class was present in the fork's ambient snapshot; if so, every test gets `fallbackAmbientDependency()`. status: documented
- **`SelectionEngine.selectAll`** — gained a 5th `Set<String> ambientDependencies` parameter; the ambient short-circuit check runs after `changedClassNames` is computed but before the per-test decision loop, so it can skip per-test dependency matching entirely once it fires (same short-circuit shape as the existing non-source-change fallback). status: documented
- **`ConsoleSummaryRenderer`/contract doc drift** — the `[blastradius]   dependency-matched: ..., fallback: ...` line is a documented, test-verified contract (`specs/002-ci-gating-plugin/contracts/mojo-and-index-contract.md`); adding a reason bucket to the renderer without updating both the contract doc and `ConsoleSummaryRendererTest`'s expected string would have let the doc, the code, and the test silently diverge. status: documented
- **Stale test caught post-hoc**: `DependencyIndexFormatTest.migratesTheKnownUnversionedLegacySchemaToTheCurrentVersion` was written before Task #2's migration-target decoupling and still asserted `migrateLegacyVersion(0) == CURRENT_VERSION` — now wrong on purpose, since the whole point of `PRE_AMBIENT_DEPENDENCIES_VERSION` is that a legacy index must *not* pass as current. Fixed by asserting against `PRE_AMBIENT_DEPENDENCIES_VERSION` instead (widened from `private` to package-private so the test can reference it symbolically). Caught by a full-reactor `mvn test` run, not by `test-compile`. status: documented
