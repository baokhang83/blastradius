# Session: expose GroundTruthResolver's BuildResult, drop the redundant probe build

- **intent:** expose GroundTruthResolver's BuildResult, drop the redundant probe build
- **started:** 2026-07-28

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

- **`GroundTruthResolution`** wraps `GroundTruthResolver.resolve()`'s `BuildResult` alongside
  its `List<GroundTruthResult>` — mechanical execution of the shape agreed in the design
  session (`design-the-safe-default-opt-in-fast-ground-truth-split.md`), no new fork here.
  `RunCommand.analyzePair` now runs `buildFailureDetector.isBuildFailure(resolution.initialBuild(), ...)`
  against the build `GroundTruthResolver` already ran, instead of first running its own
  separate, identical, agent-free probe build. Behavior is unchanged — a compile failure is
  still caught before `groundTruth` results are trusted — this only removes one redundant `mvn`
  invocation per pair. status: documented.
