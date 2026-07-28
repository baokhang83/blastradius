# Session: add fast-ground-truth integration tests and verify the full suite

- **intent:** add fast-ground-truth integration tests and verify the full suite
- **started:** 2026-07-28

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

- **`FastGroundTruthIntegrationTest`** covers the two things worth verifying specifically
  about `--fast-ground-truth` beyond what the safe-mode tests already prove: (1) a 3-commit,
  2-pair window where the middle commit is both pair 0's HEAD and pair 1's BASE — the actual
  cache-hit case — still yields a correct PASS verdict, proving a cache hit doesn't feed a
  pair a stale or partial `CommitBuild`; (2) the existing untracked-`@BeforeAll`-dependency
  would-miss fixture still produces a correct FAIL verdict when its ground-truth build is
  agent-attached instead of agent-free, proving the mode switch doesn't change pass/fail
  detection. status: documented.
- **Full suite result:** `blastradius-core` + `blastradius-validator`, 82/82 passing, 0
  failures — both new tests included, no regressions in the safe-mode (default) path.
  status: documented.
