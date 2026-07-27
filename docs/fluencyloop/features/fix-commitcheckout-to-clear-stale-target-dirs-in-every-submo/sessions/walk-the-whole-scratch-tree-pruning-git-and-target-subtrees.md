# Session: walk the whole scratch tree pruning .git and target/ subtrees, add submodule-level regression test

- **intent:** walk the whole scratch tree pruning .git and target/ subtrees, add submodule-level regression test
- **started:** 2026-07-27

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

- **CommitCheckout's reuse invariant** — `RunCommand` reuses one scratch clone across every
  commit pair in the validator's window; `checkoutCommit()` is the only place responsible for
  leaving the tree exactly as if it had never been built in before the next `mvn clean test`
  runs. Any build artifact it misses becomes a false signal for the *next* commit, not the
  current one — so bugs here show up one checkout later than the code that caused them.
  status: documented
- **BuildFailureDetector's heuristic and its blind spot** — it can only ask "does any
  `TEST-*.xml` exist anywhere under the project," not whether one is fresh. That's why
  `CommitCheckout` (not the target project's own `clean` goal) owns cleanup: the target
  project's build failing is exactly the case where its `clean` goal may never have run for
  every module. status: documented
- **Root cause found empirically, not by inspection** — running the validator against a real
  large multi-module project (apache/shenyu, dozens of modules) surfaced this; the existing
  single-module fixture and test coverage couldn't have caught it because `twoModuleReactor`
  existed as a fixture but had no test exercising `CommitCheckout`'s cleanup against it.
  status: documented

## Decision: walk the whole scratch tree with SKIP_SUBTREE, not Files.walk + filter

- **where:** `blastradius-validator/.../git/CommitCheckout.java`
- **why:** a multi-module reactor build can fail before reaching a module a prior commit tested, leaving that module's own target/ stale; only cleaning the root left BuildFailureDetector's any-TEST-*.xml heuristic fooled per-submodule. walkFileTree lets .git and each found target/ be pruned from the walk itself (SKIP_SUBTREE) instead of walking everything and filtering after — cheaper on a large reactor's object store, and target/ is deleted whole rather than visited file by file.
- **alternative:** Files.walk(scratchDir) + stream filter for dirs named target — rejected: still descends into .git and into target/ internals before the filter throws them away, wasteful on a large real-world repo like apache/shenyu
- **design:** ../design.md#sequence-two-checkouts-in-the-same-scratch-clone-one-submodule-behind-a-build-failure
- **constitution:** VII (Exhaustive Harness Cleanup)
- **trust:** ✓ verified
