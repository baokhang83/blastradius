# Session: disk-backed build cache: bound phase-1 heap and resume a crashed run

- **intent:** disk-backed build cache: bound phase-1 heap and resume a crashed run
- **started:** 2026-07-31

## Knowledge transfer

### Components (role, conditions)

- **`BuildCache`** — a disk-backed store of successful `CommitBuild`s keyed by `BuildKey` (sha + agentAttached), living at `<report>.blastradius-build-cache/`. `contains`/`load`/`store` are the whole surface. `store` writes atomically (temp file + `ATOMIC_MOVE`, falling back to a plain replace where a filesystem can't do atomic moves) so a crash mid-write can never leave a truncated file a resume would read as valid ground truth. On-disk shape is the `CachedBuild` DTO: the per-test dependency map is flattened to a `List<DependencyRecord>` (its `TestIdentity` key can't be a JSON object key without a custom Jackson key serializer — same reason `DependencyRecordReader` does it), and a `hasDependencies` boolean distinguishes an agent-free ground-truth build (null `DependencyRecordSet`) from an agent build that recorded zero tests (empty set). · status: documented
- **`BuildOutcome`** — the lightweight `(failed, failureReason)` marker `CommitBuildService.buildAll` now returns per key instead of the heavy `CommitBuild`. Carries no dependency records or ground truth — those live in the cache. This is what keeps phase 1's live heap bounded by builds-in-flight rather than the whole window. · status: documented
- **`CommitBuildService` (cache-aware)** — now takes a `BuildCache`. `buildOne` checks `cache.contains(key)` *before* borrowing a clone: a key already on disk is served as `BuildOutcome.ok()` with no checkout and no `mvn` (the resume path). On a real build it stores the success and returns only the marker; a failure is returned as a marker and deliberately NOT stored. · status: documented
- **`RunCommand` phase split (cache-threaded)** — phase 1 (`buildAllCommits`) returns `Map<BuildKey, BuildOutcome>`; phase 2 (`analyzePair`) checks the outcome for failure, then `cache.load`s the full `CommitBuild` on demand. A successful outcome with a cache miss is an `IllegalStateException` (a real invariant break, not a silent exclude). `buildCacheDirectory` derives the cache path from `--report-out`. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **The OOM was linear heap growth in the phase split, not a shenyu problem.** Phase 1 accumulated every commit's full `CommitBuild` (each ~MBs: 342 tests × their class-dependency sets + ground truth) into one map held for the entire run. At `--commits 300` on the default (¼-RAM) heap it exhausted memory ~mid-phase-1, so `report.json` was never written. The map, not the build, was the leak. · status: documented
- **The `OutOfMemoryError` log signature is deceptive.** After the OOM, the flood of `building … [with agent]` lines all stamped the same second is the `ExecutorService` draining its queued tasks as the pool tears down — not real work — each then dying with `interrupted while waiting for mvn test` / `failed to checkout`. Reading that as hundreds of builds kicking off is the trap. · status: documented
- **Cache successes only — never failures.** A failure carries only a reason string (no memory pressure), recomputes in seconds (a compile failure fails fast), and — decisively — caching a *transient* failure (e.g. the executor-shutdown interruptions this very OOM produced) would poison every later resume by permanently excluding a pair that would build fine. `store` throws on a failed build to enforce this. · status: documented
- **Atomic write is load-bearing for resume, not a nicety.** Without temp-file + `ATOMIC_MOVE`, a crash during `store` (exactly the scenario the cache exists for) could leave a half-written JSON that the next resume reads as a valid cached build → corrupt ground truth. `load` also swallows a parse/IO error as `Optional.empty()` (a miss → rebuild) so a bad entry degrades to a rebuild rather than crashing the run. · status: documented
- **Cache validity is the operator's responsibility.** The key is only (sha, agentAttached) — it does NOT fingerprint the target repo path or the tracking-agent version. Within one target that's sound (a build is deterministic given its sha; `--skip-build-extras` is soundness-neutral), but pointing the run at a different repo or upgrading the agent means the cache dir must be cleared first. Deriving the dir from `--report-out` at least stops two runs writing different reports from sharing a cache. · status: follow-up

## Decision: disk-backed build cache returning a lightweight outcome over an in-memory build map

- **where:** `blastradius-validator/.../build/CommitBuildService + BuildCache + RunCommand phase split`
- **why:** phase 1 held every commit's full CommitBuild (MBs each: 342 tests x dep sets + ground truth) in one map for the whole run, so heap grew linearly with --commits and 300 OOM'd mid-build before report.json was written; writing each build to disk and returning only a (failed,reason) marker bounds live heap to builds-in-flight and lets phase 2 load on demand
- **alternative:** keep the in-memory Map<BuildKey,CommitBuild> and just raise -Xmx — rejected: only moves the ceiling, still linear in window size, and loses the free resume-after-crash the disk store gives
- **constitution:** §VII
- **trust:** ✓ verified

## Decision: cache only successful builds, never failures

- **where:** `blastradius-validator/.../build/CommitBuildService.buildOne + BuildCache.store`
- **why:** a failed build carries only a reason string (no memory pressure) and recomputes in seconds, while caching a transient failure (e.g. the executor-shutdown interruptions the OOM itself produced) would poison every later resume by permanently excluding a pair that would build fine; store throws on a failed build to make the rule unbypassable
- **alternative:** cache failures too (as a full build result) so a re-run skips known-bad commits — rejected: a transient interruption is indistinguishable on disk from a real compile failure, so a resume would wrongly exclude recoverable pairs and silently shrink the analyzed window
- **constitution:** §III
- **trust:** ✓ verified

## Decision: atomic temp-file + ATOMIC_MOVE write, corrupt entry reads as a miss

- **where:** `blastradius-validator/.../build/BuildCache.store + load`
- **why:** the cache exists to survive a crash, so store must be crash-safe: write to a temp file then ATOMIC_MOVE into place so a crash mid-write can never leave a truncated JSON a resume would read as valid ground truth; load swallows a parse/IO error as empty so a bad entry degrades to a rebuild instead of crashing the run
- **alternative:** write JSON directly to the final path — rejected: a crash partway through the write (exactly the scenario the cache is for) leaves a half-file that the next resume parses as a real cached build, corrupting ground truth silently
- **constitution:** §III
- **trust:** ✓ verified
