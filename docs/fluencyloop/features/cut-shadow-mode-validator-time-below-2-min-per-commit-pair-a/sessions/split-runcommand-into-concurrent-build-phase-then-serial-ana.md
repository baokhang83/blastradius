# Session: split RunCommand into concurrent build phase then serial analysis, add --build-concurrency

- **intent:** split RunCommand into concurrent build phase then serial analysis, add --build-concurrency
- **started:** 2026-07-30

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

- **`RunCommand` two-phase `run()`** — now: resolve window → `buildAllCommits` (phase 1, concurrent) → `analyzeWindow` (phase 2, serial) → write report. A single fixed-thread `ExecutorService` and one `CheckoutPool`, both sized to `config.buildConcurrency()`, are created per run and closed in a `finally`. Correctness is unchanged from the old inline loop: the same builds, the same comparisons, only reordered and parallelized. · status: documented
- **`buildAllCommits` (phase 1) key enumeration** — for each pair it emits `BuildKey(base, agentAttached=true)` and `BuildKey(head, agentAttached=fastGroundTruth)`. So the base is ALWAYS agent-attached (dependency baseline); the head is agent-attached only in fast mode (reused as both baseline and ground truth) and agent-free in safe mode (independent ground truth, §III). `CommitBuildService` dedupes identical keys, so fast mode builds a shared commit once while safe mode builds it twice under different keys — see the gotcha below. · status: documented
- **`analyzePair` (phase 2) is now mode-agnostic** — it just looks up `builds.get(BuildKey(base,true))` and `builds.get(BuildKey(head, fastGroundTruth))` from the phase-1 map; if either failed, the pair is excluded. All the fast-vs-safe branching that used to live here collapsed into which keys phase 1 enumerated. Everything downstream (baseline map, changed-file classify, selection, would-miss compare, flaky detection) is byte-for-byte the old logic. · status: documented
- **`buildCommit` is now the phase-1 worker function** — signature `(CommitCheckout, sha, agentAttached, agentJar) -> CommitBuild`, passed to `CommitBuildService` as its `CommitBuilder`. No memoization inside (the service's `computeIfAbsent`-over-futures owns dedup now). Only allocates a deps temp file when `agentAttached`; returns `CommitBuild.failed(...)` on a build failure so the pair is excluded rather than the run aborting. · status: documented
- **`--build-concurrency K` (RunConfig + Main)** — new optional flag, `RunConfig.buildConcurrency`, default `1` = today's exact serial behavior (pool of 1, single-thread executor), so existing callers and every prior constructor overload are untouched (a new 6-arg canonical constructor with 3-, 4-, 5-arg delegators). Validated `>= 1` in the compact constructor. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **A `build/` gitignore rule was silently swallowing the whole `validator.build` source package** — `.gitignore` line 32's Gradle-style `build/` matches ANY dir named `build` at any depth, including the Java package `io...validator.build`. Already-tracked files there (e.g. `GroundTruthResolver.java`) survive because gitignore never un-tracks, which is exactly why it was invisible — but every NEW file added to that package (`CheckoutPool`, `CommitBuild`, `CommitBuildService`, and both tests) was ignored and would have been silently dropped from the commit, breaking the build on checkout. Fixed with a re-include `!**/src/**/build/` (Gradle only writes to a module-root `build/`, never under `src/`, so this is safe). · status: documented
- **Safe mode legitimately builds an internal commit twice** — because pairs are consecutive (head of pair i = base of pair i+1), a middle commit is enumerated once as `(sha, agent=false)` (its head role) and once as `(sha, agent=true)` (its base role). These are DIFFERENT `BuildKey`s and must NOT be collapsed: merging them would reuse an agent-attached build as ground truth, destroying §III's independence. Fast mode enumerates both as `(sha, true)`, so the dedup collapses them to one build — the entire fast-vs-safe build-count difference lives in that one boolean. · status: documented
- **Phase 2's `scopeCheckout` borrow never blocks** — phase 1 has fully drained (`buildAll` joined all futures) before phase 2 borrows, so every clone is idle; the single borrow-for-the-whole-phase is just a convenient way to reuse one clone for the occasional NON_SOURCE reactor-scope materialization, released in a `finally`. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: build-count difference between fast and safe mode lives entirely in the head BuildKey's agent flag

- **where:** `blastradius-validator/.../cli/RunCommand.java (buildAllCommits + analyzePair)`
- **why:** enumerating base as agent=true and head as agent=fastGroundTruth makes phase 2 mode-agnostic and lets CommitBuildService's dedup do all the work: fast collapses a shared commit to one build, safe keeps the base-with-agent vs head-without-agent builds distinct
- **alternative:** keep the fast/safe branching inside analyzePair as before — rejected: duplicates the build-orchestration logic across two phases and couples the concurrency lever to the mode
- **constitution:** §III
- **trust:** ✓ verified

## Decision: re-include src build packages in gitignore rather than rename the package

- **where:** `.gitignore`
- **why:** the Gradle-style build/ rule silently ignores the io...validator.build source package, so new files there would drop from commits; a scoped !**/src/**/build/ re-include fixes it without touching the package name or the many files that reference it
- **alternative:** rename the Java package away from 'build' — rejected: churns imports across the module and fights a legitimate, conventional package name to satisfy a too-broad ignore rule
- **trust:** ✓ verified
