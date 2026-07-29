# Session: scope the fallback in the selection engine

- **intent:** scope the fallback in the selection engine
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

- **`TestModuleIndex`** — resolves the design's open decision (option 1, package-root scan): maps a `TestIdentity` (class FQN, no path) to its owning `ModuleId` by walking each module's `src/test/{java,kotlin}` root once per commit pair and attributing every source file through the same `ReactorModuleGraph.moduleOf(path)` the changed-file side uses. One source of truth for path→module. Handles Java and Kotlin; skips `target/`/`build/`. · status: documented
- **`ReactorScope`** — the engine's collaborator answering "which tests can this NON_SOURCE change set affect?" Bundles the graph + `TestModuleIndex`. `isReactorWide(changed)` gates the whole-suite escape; `forChanges(changed)` computes the affected-module set (union of each changed file's module + transitive dependents); `affects(test)` tests membership, conservatively true when a test's module can't be resolved. · status: documented
- **`SelectionEngine.selectAll` (6-arg overload)** — added alongside the original 5-arg signature (which now delegates with `reactorScope = null`), so existing Maven/Gradle plugin callers compile unchanged. When a scope is present and the change isn't reactor-wide, tests in affected modules get `FALLBACK_NON_SOURCE_DEPENDENT_MODULE`; everything else flows through the normal per-test pipeline (dependency-match / new-or-modified) — so a pair mixing a resource change with code changes still gets precise selection for the unaffected modules. · status: documented
- **`SelectionReason.FALLBACK_NON_SOURCE_DEPENDENT_MODULE`** — new explainable reason distinguishing a *scoped* fallback selection from the whole-suite `FALLBACK_NON_SOURCE_CHANGE`, so reports show why a test ran (its module changed or depends on a changed module). · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Ambient fallback outranks reactor scoping** — the ambient-dependency whole-suite escape is checked *before* applying the scoped-fallback branch: a class loaded before any tracking window opened has no trustworthy per-test data, so no module can be soundly ruled out for it. Scoping a pair that also tripped ambient fallback would be unsound. · status: documented
- **Null scope ≡ old behavior** — passing `reactorScope = null` reproduces the exact whole-suite fallback, byte for byte. This is what lets the two plugin callers keep working before slice 3 wires a real scope, and is asserted by `nullReactorScopePreservesWholeSuiteFallback`. · status: documented
- **Two soundness escape hatches, both widen never narrow** — a reactor-wide change (root/parent pom, or a file under no leaf module) selects everything; a test whose own module is unresolvable is treated as affected. Both bias toward over-selection, satisfying §III. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: add a 6-arg selectAll overload with a nullable ReactorScope rather than change the signature

- **where:** `blastradius-core/.../selection/SelectionEngine.java`
- **why:** keeps the existing 5-arg callers (Maven plugin, Gradle plugin, current validator) compiling unchanged while opting the validator into scoping via the new overload; null scope reproduces whole-suite fallback exactly
- **alternative:** change the single selectAll signature to require a ReactorScope — rejected: forces every caller to build a graph immediately and couples unrelated plugin work to this feature
- **design:** ../design.md
- **trust:** ✓ verified

## Decision: check the ambient-dependency fallback before applying reactor scoping

- **where:** `blastradius-core/.../selection/SelectionEngine.java`
- **why:** an ambient class has no trustworthy per-test dependency data, so no module can be soundly ruled out for it; scoping a pair that also tripped ambient fallback would silently skip tests
- **alternative:** apply reactor scoping first / independently — rejected: unsound, reintroduces the false-skip §III forbids
- **design:** ../design.md
- **constitution:** §III
- **trust:** ✓ verified

## Decision: resolve TestIdentity to a module by scanning test-source roots through graph.moduleOf

- **where:** `blastradius-core/.../reactor/TestModuleIndex.java`
- **why:** the design's chosen option (package-root scan); reusing graph.moduleOf keeps one path→module source of truth (deepest-match + reactor-wide safety) and needs no on-disk index format change, so it works for the validator's historical replay
- **alternative:** record each test's module at tracking time — rejected here: changes the index format (versioning cost); kept as a possible follow-up if layout assumptions bite
- **design:** ../design.md#open-decision
- **trust:** ✓ verified
