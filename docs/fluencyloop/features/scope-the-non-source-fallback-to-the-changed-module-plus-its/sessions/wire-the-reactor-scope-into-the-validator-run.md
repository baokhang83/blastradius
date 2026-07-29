# Session: wire the reactor scope into the validator run

- **intent:** wire the reactor scope into the validator run
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

- **`RunCommand.buildReactorScope(checkout, headCommit)`** — the validator-side wiring that turns the engine capability into real savings: checks out the head commit, builds the `ReactorModuleGraph` + `TestModuleIndex` from that tree, and hands the resulting `ReactorScope` to the new 6-arg `selectAll`. One scope per pair. · status: documented
- **`RunCommand.analyzePair` selection call** — now passes the reactor scope as the 6th arg. Everything upstream (ground truth, dependency baseline, changed-file classification) is unchanged; only the fallback breadth narrows. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Explicit head re-checkout before building the graph** — in `--fast-ground-truth` mode, `buildCommit` memoizes per-commit results and *skips* the checkout on a cache hit, so the scratch clone can be left reflecting the *base* tree when head was cached first. Building the graph from the scratch dir as-is would silently use base's module layout/edges. `buildReactorScope` therefore re-checks-out head explicitly. Safe because build results are already extracted into memory by that point (so `CommitCheckout` wiping `target/` is harmless), and a git checkout is negligible next to a Maven build (so fast mode's caching win survives). · status: documented
- **Graph-build failure → null scope → whole suite** — `buildReactorScope` catches any `RuntimeException` (a malformed/unusual POM in some historical commit) and returns `null`, so `selectAll` falls back to the exact whole-suite behavior instead of aborting the pair or guessing a narrower scope. Widen-never-narrow on uncertainty (§III). · status: documented
- **Integration-test failures are environmental** — `DependencyTrackingIntegrationTest` (5 cases) fails with `Cannot run program "mvn"`: it shells out via `ProcessBuilder`, which needs `mvn.cmd` on the raw PATH on Windows. Pre-existing, unrelated to this feature (which touches selection/reactor, not the tracking-agent subprocess path). The other 106 core tests pass. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: re-check-out head before building the reactor graph rather than reuse the scratch working dir

- **where:** `blastradius-validator/.../cli/RunCommand.java (buildReactorScope)`
- **why:** in --fast-ground-truth mode a cached head build skips its checkout, so the scratch clone may still reflect base; building the graph from the wrong tree could silently mis-scope the fallback. build results are already extracted, so wiping target/ is harmless and a checkout is cheap next to a build
- **alternative:** build the graph from whatever the scratch dir currently holds — rejected: base/head POM layout can differ, an unsound assumption in the tool that measures soundness
- **design:** ../design.md
- **constitution:** §III
- **trust:** ✓ verified

## Decision: catch graph-build failures and fall back to a null scope (whole suite)

- **where:** `blastradius-validator/.../cli/RunCommand.java (buildReactorScope)`
- **why:** a malformed or unusual POM in some historical commit must not abort the pair or narrow selection; returning null makes selectAll reproduce the safe whole-suite fallback
- **alternative:** let the exception propagate / exclude the pair — rejected: loses a measurable pair over a recoverable parse issue, and any narrower guess would be unsound
- **design:** ../design.md
- **constitution:** §III
- **trust:** ✓ verified
