# Session: core: agent + boundary listener ambient snapshot

- **intent:** core: agent + boundary listener ambient snapshot
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

## Knowledge transfer

- `DependencyTrackingAgent.ambientDependencies`/`ambientSnapshotTaken`: a `ConcurrentHashMap.newKeySet()` and an `AtomicBoolean`, both instance-scoped (per fork, since `installedAgent` is one per JVM). `snapshotAmbientDependencies()` is guarded by `compareAndSet(false, true)` so only the very first caller across all threads actually iterates `loadedClasses()`; every later call in the same fork is a no-op. Status: documented.
- `recordAmbientSnapshot()` (static) mirrors the existing `recordTestStarted(TestIdentity)` entry-point pattern: null-check `installedAgent`, delegate to an instance method. Kept consistent with the rest of the class rather than inventing a new shape. Status: documented.
- `TestBoundaryListener.executionStarted` now calls `recordAmbientSnapshot()` as its very first statement, before `recordTestStarted(test)` — ordering matters: the ambient snapshot must be taken before the first test's own tracking window opens, otherwise classes the first test itself loads would be wrongly folded into the "ambient" (pre-test) set. Status: documented.
- `ambientDependencies()` public accessor returns `Set.copyOf(...)` (immutable snapshot), same convention as `recordedDependencies()`'s `Map.copyOf` per-entry — callers (the not-yet-built `TrackRunner`/`DependencyIndex` wiring) can't mutate agent-internal state. Status: documented.
- Follow-up (not yet built): this set is currently only readable in-process; it still needs a path to disk (Task #2 — record writer/reader) before `TrackRunner` can pull it into the persisted `DependencyIndex`. Status: follow-up.

## Decision: snapshot ambient classes at first executionStarted, not agent install

- **where:** `blastradius-core/.../tracking/DependencyTrackingAgent.java, TestBoundaryListener.java`
- **why:** premain() runs before JUnit Platform's discovery pass builds the TestPlan, so a snapshot taken at install time would miss exactly the classes discovery force-loads — the first test boundary is the earliest point after discovery has already run, guarded idempotent via an AtomicBoolean so later tests are no-ops
- **alternative:** canRetransform=true + retransformClasses() at every executionStarted — rejected: only reassigns 'first loader wins' to whichever test runs right after the retransform, and re-running it per test is expensive across a large classpath
- **design:** ../design.md#sequence-before-bug-vs-after-fix
- **trust:** ⚠ not independently verified
