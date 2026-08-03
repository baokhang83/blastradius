# Session: fan phase-2 mutation validation across the idle checkout pool

- **intent:** fan phase-2 mutation validation across the idle checkout pool
- **started:** 2026-08-03

---

## Knowledge transfer

_The ground this slice makes understandable — components, roles, and conditions explained,
persisted so the fluency doesn't evaporate with the conversation. About the work, never a person._

### Components (role, conditions)

- **`analyzeWindow` (RunCommand)** — phase 2's orchestrator. Submits one `analyzeOnePair` task per pair to the executor phase 1 already built, collects `Future<PairOutcome>`s, then drains them in submission order and folds each into the report. Receives the `ExecutorService` from `run()` (a fixed pool sized by `--build-concurrency`). · status: documented
- **`analyzeOnePair` + `PairOutcome` (RunCommand)** — one pair computed end-to-end on its own borrowed clone: `pool.borrow()` → `analyzePair` (selection/compare) → `mutationValidator.validate` (the expensive per-mutant builds) → `pool.release()` in `finally`. Returns a self-contained `PairOutcome(index, analysis, mutation, millis)`; the `index` is what lets the caller fold results in window order. Never throws — any analysis/mutation failure becomes an excluded pair. · status: documented
- **`CheckoutPool` as the concurrency throttle** — `borrow()` blocks (LinkedBlockingQueue.take) once all clones are lent out, so although every pair is submitted at once, at most `--build-concurrency` run concurrently. No new CLI knob was added (§II). · status: documented
- **`MutationCache`** — phase 2's disk-backed cache, a structural twin of phase 1's `BuildCache`: atomic temp-file-then-move writes, corrupt-file-reads-as-miss, lives beside the report at `<report>.blastradius-mutation-cache/`. `validate()` checks it before each mutant (hit → served free, no build, no deadline charge; miss → build then `store`). Serves crash-resume (skip mutants already completed) and bounds heap (each `MutationExperiment` written, not held for the whole window). Constructed only when mutation validation is on; passed through `analyzeWindow` → `analyzeOnePair` → `validate`. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Determinism depends on ordered drain, not as-completed append (§IV)** — pairs finish out of order, so tasks must NOT mutate shared accumulation lists mid-run. They return an indexed `PairOutcome`; the report is folded from `future.get()` in submission (window) order, making output byte-identical regardless of finish order. · status: documented
- **`reactorScopeCache` had to become `ConcurrentHashMap`** — it's the one mutable structure shared across concurrent pairs (the per-head-commit scope memo). `computeIfAbsent` keeps the repeated-head saving race-free. The other shared reads — `BuildCache` (stateless, distinct file per key), the `outcomes` map (read-only after phase 1), and the stateless `GroundTruthResolver`/`MavenBuildRunner` — are safe as-is because each task runs against its own `projectDir` clone. · status: documented
- **The `finally` release is load-bearing** — the pool-as-throttle invariant only holds if every borrowed clone is returned; a task that borrowed but never released would permanently shrink (and eventually starve) the pool. · status: documented
- **Cache key deliberately omits the bounding config** — sound only because `analyzeMutant` genuinely never reads `MutationValidationConfig`; the seven-field mutant identity fully determines the outcome. If a future change made a mutant's result depend on the config, the key would need to grow or resume would serve stale evidence. · status: documented
- **Deadline loop had to become skip-and-continue** — after adding the cache, the old break-on-first-post-deadline loop would have stopped scanning at the first uncached post-deadline mutant and skipped free cached hits sitting later in the candidate list. Now only uncached mutants that need a build are time-skipped; cached hits are always served. · status: documented

---

## Decision: fan phase 2 across whole pairs, not within a pair

- **where:** `blastradius-validator/src/main/java/io/github/baokhang83/blastradius/validator/cli/RunCommand.java (analyzeWindow / analyzeOnePair)`
- **why:** Phase 2 ran serially on one borrowed clone while the other build-concurrency clones sat idle after phase 1; the per-mutant -pl/-am/-amd Maven builds are the hot spot (a single -amd pair took ~50 min). One task per whole pair, each borrowing its own clone, reuses phase 1's executor + CheckoutPool with no new CLI knob (§II) and fills cores even when pairs have a single mutant.
- **alternative:** Parallelize within a pair (nested, per-mutant executor): more complex, and the pool would need per-pair sub-throttling; a separate --analysis-concurrency knob: extra config for no benefit since the existing pool already sizes concurrency.
- **design:** ../design.md
- **constitution:** II
- **trust:** ✓ verified

## Decision: ordered future drain + ConcurrentHashMap over as-completed append

- **where:** `blastradius-validator/src/main/java/io/github/baokhang83/blastradius/validator/cli/RunCommand.java (analyzeWindow)`
- **why:** Pairs finish out of order; folding results as-completed would make the report depend on finish order. Tasks return an indexed PairOutcome and the report is folded from future.get() in submission (window) order, so output is byte-identical regardless of scheduling (§IV). reactorScopeCache — the one shared mutable structure — becomes a ConcurrentHashMap so the repeated-head memo stays race-free; the other shared reads (BuildCache, read-only outcomes map, stateless resolvers) are safe as-is.
- **alternative:** Append to shared lists as each task completes: non-deterministic output, violates §IV, and needs locking on every accumulation list anyway.
- **constitution:** IV
- **trust:** ✓ verified

## Decision: key the mutation cache on mutant identity, not the bounding config

- **where:** `blastradius-validator/src/main/java/io/github/baokhang83/blastradius/validator/mutation/MutationCache.java (fileFor)`
- **why:** A mutant's build + selection comparison is deterministic given its pair (base->head) and its single-token edit (sourcePath, offset, operator, original, replacement); analyzeMutant never reads the bounding config. So the cache key is sha256 of those seven fields only. Excluding --max-mutations-per-pair and the class filter means a re-run with a WIDER bound still reuses every mutant a narrower run already completed — maximum resume hits, which is the whole point (crash-resume + memory-bounding, mirroring phase 1's BuildCache).
- **alternative:** Fold the bounding config into the key: would invalidate the entire cache whenever a bound changed, defeating resume for exactly the long runs that motivate it, for no correctness gain since the bound doesn't affect any single mutant's outcome.
- **constitution:** IV
- **trust:** ✓ verified

## Decision: cache only compilable mutants; skip-and-continue past the deadline instead of breaking

- **where:** `blastradius-validator/src/main/java/io/github/baokhang83/blastradius/validator/mutation/HistoricalMutationValidator.java (validate) + MutationCache.store`
- **why:** MutationCache.store rejects UNBUILDABLE (mirrors BuildCache refusing failed builds): an unbuildable mutant recompiles in seconds and could be a transient interruption that, cached, would poison every resume. Because a cache hit is now free, the deadline loop changed from break-on-first-post-deadline to skip-and-continue, so a resumed run past its time budget still serves later cached hits rather than abandoning them; timeLimitSkipped counts only uncached mutants that genuinely needed a build.
- **alternative:** Cache every outcome incl. UNBUILDABLE: risks permanently excluding a mutant that failed transiently. Keep the break-on-deadline loop: after adding the cache it would stop scanning at the first post-deadline miss and skip free cached hits sitting later in the candidate list.
- **constitution:** III
- **trust:** ✓ verified
