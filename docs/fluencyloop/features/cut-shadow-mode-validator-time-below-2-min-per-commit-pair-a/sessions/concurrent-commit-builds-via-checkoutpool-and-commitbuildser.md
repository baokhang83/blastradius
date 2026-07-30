# Session: concurrent commit builds via CheckoutPool and CommitBuildService

- **intent:** concurrent commit builds via CheckoutPool and CommitBuildService
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

- **`CheckoutPool`** — a fixed-size pool of independent `CommitCheckout` clones so up to `size` commit builds run at once without stomping each other's working tree / `target/`. Clones are made once, up front, in `of(...)` (cloning is the expensive part — never per build); a worker `borrow()`s a clone (blocking via a `LinkedBlockingQueue.take()` when all are in use), checks out + builds on it, then `release()`s it. `close()` is idempotent and deletes every clone; the target repo itself is only ever read from. Reusing whole `CommitCheckout` instances (not re-cloning per build) preserves §VII's exhaustive `target/` cleanup on every checkout and avoids re-cloning a large repo K times per commit. · status: documented
- **`CommitBuildService` (phase 1)** — enumerates the set of builds a window needs, submits each distinct one to an `ExecutorService`, and returns a `Map<BuildKey, CommitBuild>`. This is the redesign's core wall-clock lever: a single `-T` reactor build leaves cores idle on its critical path, and running several commits' builds concurrently (each in its own clone) fills them. Deduplicates by `BuildKey` (a commit shared by consecutive pairs is built once) via `computeIfAbsent` over a `LinkedHashMap<BuildKey, Future>`. Blocks in `buildAll` until every future resolves. · status: documented
- **`BuildKey` (sha, agentAttached)** — the identity that both dedupes and memoizes builds. Same sha + different `agentAttached` = two genuinely different builds (safe mode's base-with-agent vs. head-without-agent), so they are NOT collapsed; identical keys are. · status: documented
- **`CommitBuild` (promoted to top-level)** — was a private record inside `RunCommand`; now a top-level record in the `build` package with `succeeded(deps, groundTruth)` / `failed(reason)` factories, so both `CommitBuildService` and (slice 3) `RunCommand`'s phase 2 share one shape. `dependencyRecordSet` is null for an agent-free ground-truth build. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **The pool, not the executor, is the concurrency throttle** — an 8-thread executor with a pool of 2 still runs at most 2 builds at once, because a worker blocks on `borrow()` until a clone frees up. This is why the executor can be sized generously (or shared) while build parallelism is bounded exactly by the number of isolated working copies on disk — the real scarce resource. Verified by `neverRunsMoreConcurrentBuildsThanThePoolHasClones`. · status: documented
- **A thrown builder must not sink the phase** — `buildOne` wraps the builder call so a `RuntimeException` (e.g. an I/O error materializing a checkout) becomes a `CommitBuild.failed(...)` for that one key; every other commit still finishes (FR-009). Consequently `buildAll` only ever sees an `ExecutionException` for an orchestration bug, which it rethrows rather than masks. The `finally` releases the clone on every path so a failed build never leaks a pool slot (a leak would deadlock the next `borrow()`). · status: documented
- **Shared `MavenBuildRunner`/`GroundTruthResolver` are safe under concurrency** — both hold only final config (e.g. `-T` threads) and no mutable per-run state, so one instance can serve all build workers; isolation comes entirely from each worker owning a distinct `CommitCheckout` working directory. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: pool is the concurrency throttle, not the executor

- **where:** `blastradius-validator/.../build/CommitBuildService.java`
- **why:** bounding parallel builds by the number of isolated working copies (the scarce on-disk resource) lets the executor be sized freely while at most pool-size builds ever run at once
- **alternative:** cap concurrency with a fixed-thread executor and clone per submission — rejected: re-clones a large repo per build and decouples the limit from the real constraint (disjoint target/ dirs)
- **constitution:** §VII
- **trust:** ✓ verified
