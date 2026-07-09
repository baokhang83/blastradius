# Phase 1 Data Model: CI-Gating Maven Plugin

Derived from the spec's Key Entities. Entities reused unchanged from `blastradius-core` (moved
from the validator, see plan.md's Project Structure) are referenced, not redefined — see
`specs/001-shadow-mode-validator/data-model.md` for `TestIdentity`, `ChangedFile`, and the
`SelectionReason` enum's existing values (`DEPENDENCY_MATCH`, `FALLBACK_NON_SOURCE_CHANGE`,
`NEW_OR_MODIFIED_TEST`, `NO_MATCH`) — this feature adds no new selection reasons; it applies the
same ones live instead of only reporting on them.

## DependencyIndex

The persisted, cacheable record a track run produces and a select run consumes (Key Entity:
Dependency Index; research.md #2, #3).

| Field | Type | Notes |
|---|---|---|
| `anchorCommit` | commit SHA | The base reference commit this index was built against (research.md #3) — not a branch name, a specific commit |
| `builtAt` | timestamp | When the track run that produced this index completed |
| `testDependencies` | map\<TestIdentity, set\<string\>\> | Per test, the set of class names it was observed to depend on. Note: `blastradius-core`'s tracking agent also records a bytecode checksum per class (validator research.md #1), but the proven `SelectionEngine`/`DependencyMatchSelector` logic only ever consumes class *names* — a changed class only ever appears in a diff if its content changed, making the checksum redundant with the diff itself for this selector. The index therefore persists names only; carrying the checksum forward would be new, unvalidated surface area this feature doesn't need. |

## IndexApplicability

Not persisted — a computed result each `blastradius:select` invocation derives before deciding
whether to use the index or fall back (research.md #3).

| Field | Type | Notes |
|---|---|---|
| `status` | enum: `APPLICABLE`, `MISSING`, `UNREADABLE`, `ANCHOR_UNREACHABLE` | Only `APPLICABLE` uses the index; every other value triggers fallback (FR-007) |
| `index` | DependencyIndex, nullable | Present only when `status = APPLICABLE` |

## CurrentChanges

The live build's own analog of the validator's `CommitPair`/`ChangedFile` — computed once per
`blastradius:select` invocation, not persisted.

| Field | Type | Notes |
|---|---|---|
| `baseReference` | string | The configured base git reference (e.g. the default branch) |
| `resolvedBaseCommit` | commit SHA | `baseReference` resolved to a concrete commit at invocation time |
| `currentCommit` | commit SHA | The commit actually being built |
| `isBaseRefBuild` | boolean | `currentCommit == resolvedBaseCommit` — this build IS the base reference (typically a post-merge/trunk build), the trigger for `TRACK` mode (research.md #1) |
| `changedFiles` | list\<ChangedFile\> | Diff between `resolvedBaseCommit` and the current working tree/branch (`blastradius-core`'s `ChangedFile`/`FileKind` types, unchanged); meaningless/empty when `isBaseRefBuild = true` |

## SelectionDecision

Reused unchanged from `blastradius-core` (moved from the validator) — one decision per test for
the current build, using the same `SelectionReason` values. The live context adds no new reason;
it's the same enum the validator already proved is exhaustive and safe.

## BuildReport

The plugin's per-build output (Key Entity: Build Report; FR-008, FR-009).

| Field | Type | Notes |
|---|---|---|
| `mode` | enum: `TRACK`, `SELECT`, `FALLBACK` | Which path this invocation took (research.md #1). `TRACK`: `isBaseRefBuild` (or explicit `-Dblastradius.mode=track`) — full suite runs, and a subprocess refreshes the index. `SELECT`: a valid index applies — narrowed to the selected subset. `FALLBACK`: no valid index and *not* a base-ref build — full suite runs, same as `TRACK`, but no subprocess is forked (research.md #1's "why not track on every miss") |
| `indexApplicability` | IndexApplicability | Why `SELECT` was or wasn't chosen |
| `decisions` | list\<SelectionDecision\> | One per test in the suite; empty for `TRACK`/`FALLBACK`, where the *entire* suite ran unconditionally rather than per-test decisions being computed |
| `selectedCount` | int | Tests that actually ran |
| `totalCount` | int | Tests in the full suite |
| `updatedIndex` | DependencyIndex, nullable | Present only when `mode = TRACK` — the freshly (re)built index this run produced, now persisted for future `SELECT` runs. Always absent for `FALLBACK`, which deliberately does not attempt an index refresh (research.md #1) |

## Relationships

```
CurrentChanges ──resolved via git──▶ baseReference, resolvedBaseCommit, currentCommit, isBaseRefBuild, changedFiles
IndexApplicability ──computed from──▶ persisted DependencyIndex (if any) + CurrentChanges.resolvedBaseCommit

CurrentChanges.isBaseRefBuild = true  (or -Dblastradius.mode=track)
  ──▶ TRACK mode: this build's own suite runs full & unfiltered; separately, a subprocess
      `mvn test` with the tracking agent attached produces a fresh DependencyIndex

CurrentChanges.isBaseRefBuild = false AND IndexApplicability = APPLICABLE
  ──▶ SELECT mode: SelectionEngine(allTests, index.testDependencies, changedFiles)
      ──produces many──▶ SelectionDecision (one per test)
      ──▶ selected tests handed to Surefire/Failsafe as a -Dtest= filter

CurrentChanges.isBaseRefBuild = false AND IndexApplicability ≠ APPLICABLE
  ──▶ FALLBACK mode: full suite runs, no subprocess forked, no index produced

(TRACK | SELECT | FALLBACK) ──produces──▶ BuildReport
```
