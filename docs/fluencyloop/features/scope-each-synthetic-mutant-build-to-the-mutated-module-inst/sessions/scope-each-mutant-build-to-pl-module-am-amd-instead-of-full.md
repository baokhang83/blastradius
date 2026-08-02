# Session: Scope each mutant build to -pl module -am -amd instead of full clean reactor

- **intent:** Scope each mutant build to -pl module -am -amd instead of full clean reactor
- **started:** 2026-08-02

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

- **`MavenBuildRunner.command(selector, modulePath, clean, alsoMakeDependents)`** — the new
  4-arg overload adds an `-amd` (also-make-dependents) dimension on top of the existing
  `-pl <module> -am`. `-amd` is only emitted when a `modulePath` is present (it is meaningless
  without `-pl`, which is why the null-module case adds nothing). The 3-arg overload delegates
  with `alsoMakeDependents=false`, so the single-test `confirmFailure` rerun path is byte-for-byte
  unchanged. · status: documented
- **`MavenBuildRunner.run(projectDir, agentJar, depFile, modulePath)`** — new scoped full-suite
  overload. Keeps `clean=true` (each mutant runs on a freshly re-checked-out tree) and passes
  `alsoMakeDependents=true`. The old 3-arg `run` now delegates with `modulePath=null` (whole
  reactor), so it is a strict superset. · status: documented
- **`GroundTruthResolver.resolve(..., modulePath)`** — threads `modulePath` into the scoped
  `run`. The failure-confirmation reruns underneath stay scoped to each *failing test's own*
  module (the tighter scope), so only the initial full-suite build widened to module+dependents.
  · status: documented
- **`HistoricalMutationValidator.buildModuleGraph` / `modulePathFor`** — builds one
  `ReactorModuleGraph` per pair from the head tree, then resolves each candidate's `sourcePath`
  (repo-relative, produced by `MutationCandidateGenerator`) to its owning `ModuleId.relativePath()`
  via `graph.moduleOf(...)`, which is exactly the `-pl` argument. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Batching all mutations into one build is unsound — one mutant per build is mandatory** — a
  combined mutant widens the `B -> M` diff to every mutated file, so Blastradius counts a killing
  test as "correctly selected" when it only touched a *co-mutated* file it doesn't depend on.
  That reports PASS where per-mutation isolation reports a would-miss — a false PASS, the dangerous
  direction for a shadow-mode gate (§III). · status: documented
- **`-amd` is load-bearing, not an optimization knob** — a single-file mutation in module X can be
  killed by a test in a *downstream* module Y that exercises X's API. `-am` only pulls X's upstream
  deps (so X compiles) and would build Y out entirely; the selected killing test in Y would never
  run and get misreported as a skipped killer (a fabricated would-miss / false FAIL). · status: documented
- **Skipping `clean` was NOT how the cost got cut — the `target/` wipe stays reactor-wide** —
  `CommitCheckout.checkoutCommit()` already deletes *every* module's `target/` before each mutant,
  which is what keeps `BuildFailureDetector`'s whole-tree `TEST-*.xml` scan from being fooled by a
  stale report (§VII). The saving is entirely from `-pl` shrinking the built module set, not from
  dropping `clean`. · status: documented
- **Graph-unavailable and root-owned paths both fall back to the whole reactor** — if the reactor
  graph can't be built, or a path maps to no module / to the reactor root, `modulePathFor` returns
  `null` and the build is unscoped. Never guess a narrower scope than the graph proves (§III). · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: scope the mutant build to -pl module -am -amd, one mutant per build

- **where:** `blastradius-validator/.../mutation/HistoricalMutationValidator.java`
- **why:** A mutant changes one file in one module; only that module and its dependents can change test outcome, so -pl module -am -amd builds the exact blast radius instead of the whole reactor, cutting ~100 serial full rebuilds to a handful of modules each while keeping the verdict sound.
- **alternative:** Batch all mutations into one build (one build per pair) — rejected: widens the B->M diff to every mutated file, so a killing test counts as correctly-selected when it only touched a co-mutated file, reporting a false PASS (§III).
- **design:** ../design.md
- **constitution:** §III, §IX
- **trust:** ✓ verified
