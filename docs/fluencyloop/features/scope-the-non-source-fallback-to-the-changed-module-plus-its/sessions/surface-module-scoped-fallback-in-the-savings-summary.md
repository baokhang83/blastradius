# Session: surface module-scoped fallback in the savings summary

- **intent:** surface module-scoped fallback in the savings summary
- **started:** 2026-07-29

<!--
FluencyLoop Stage 3 — a session is a slice of the build. It holds two persistent records:
  1. Knowledge transfer — what the developer was made fluent in this slice (you write it).
  2. Decisions — the genuine forks, appended by `fluencyloop decision` (the script formats them).

Everything below is scaffolding in comments — nothing to delete. Write knowledge transfer under
its headings; add each decision with
  fluencyloop decision --where <file/area> --why <rationale> [--alternative <rejected + why>] \
                       [--title <chose X over Y>] [--constitution §N] [--trust verified|unverified]
so the block is formatted deterministically and you never hand-write the bullet schema. No
`commits:` field: the feature is a branch, so the PR view derives commits live from git.

KNOWLEDGE-TRANSFER — one bullet per component/role/mechanism explained:
  **<subject>** — <what it does, under what conditions> · status: documented | follow-up
  Make it RICH: cover the inventory AND the non-obvious, hard-won lessons (a bug's root cause,
  why something is done an odd way, a documented limitation). Describe the WORK, never a person
  (no competence, no "who knew what") — these files are committed and name an author via git.

DECISION fields (assembled by `fluencyloop decision`):
  where        — file/area (NOT a line number — survives refactoring)
  why          — the rationale, taught live before it was written
  alternative  — the rejected option and why (what makes it rationale, not description)
  design       — (optional) ../design.md#anchor
  constitution — (optional) §N
  trust        — ✓ verified | ⚠ not independently verified (about the DECISION, never the person)
-->

---

## Knowledge transfer

_The ground this slice makes understandable — components, roles, and conditions explained,
persisted so the fluency doesn't evaporate with the conversation. About the work, never a person._

### Components (role, conditions)

- **`SavingsSummary.moduleScopedFallbackSelections`** — new record component isolating reactor-scoped fallback selections from whole-suite `fallbackDrivenSelections`. This is the feature's headline metric: on the shenyu run, everything that used to be counted as whole-suite `fallbackDrivenSelections` can now split into "still whole-suite" vs "scoped to a changed module + dependents" — the second is the savings this feature recovers. · status: documented
- **`SavingsSummaryAggregator`** — now counts `FALLBACK_NON_SOURCE_DEPENDENT_MODULE` into the new bucket. `proportionSkipped` was always derived from `selected/total`, so the headline skip rate was already correct; this restores the per-reason breakdown. · status: documented
- **`TextSummaryRenderer`** — adds a "module-scoped fallback" line to the human-readable summary. JSON serialization is Jackson-reflective over the record, so the new field appears in the report automatically. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Invariant restored, not just extended** — `SavingsSummary` documents `dependencyMatched + fallbackDriven + newOrModified == totalSelected`, asserted in two tests. Introducing the scoped-fallback reason in slice 2/3 silently broke it (those selections are `selected` but in no bucket); adding a class-load agent could regress it the same way. The fix adds `moduleScopedFallback` as a fourth term. **Note:** `FALLBACK_AMBIENT_DEPENDENCY` is still uncounted here — a pre-existing gap left untouched (out of this feature's scope), so the invariant holds only because the validator's ambient path is rare; worth a follow-up. · status: follow-up
- **Environmental test failures are broad on this machine** — every validator/core test that shells out to `mvn` via `ProcessBuilder` errors with `Cannot run program "mvn"` (Windows resolves `mvn.cmd`, not `mvn`, and ProcessBuilder doesn't). ~20 tests across `MavenBuildRunnerTest`, `GroundTruthResolverTest`, `BuildFailureDetectionTest`, `AgentOverheadMeasurementTest`, `DependencyTrackingIntegrationTest`. None touch selection/reactor/report code; all report + reason-referencing unit tests pass. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: add a fourth moduleScopedFallback bucket to SavingsSummary rather than fold it into fallbackDriven

- **where:** `blastradius-validator/.../report/SavingsSummary.java`
- **why:** the scoped-fallback reason broke the documented bucket-sum invariant, and folding it into fallbackDriven would hide the feature's whole point — how much of the old whole-suite fallback is now narrowed; a separate bucket both restores the invariant and surfaces the headline metric
- **alternative:** reuse the existing fallbackDriven bucket — rejected: makes the savings this feature recovers invisible in the report, defeating the reason for building it
- **design:** ../design.md
- **trust:** ✓ verified
