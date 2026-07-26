# Session: Specify conservative stale baseline selection

- **intent:** Specify conservative stale baseline selection
- **started:** 2026-07-26

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

## Decision: Reuse only a reachable stale baseline with a widened diff

- **where:** `IndexApplicabilityResolver`, `CurrentChangesResolver`, and `SelectMojo`
- **why:** A cache race can leave the exact target-branch index unavailable even though a valid predecessor index is present; widening the comparison from a reachable predecessor preserves safe test selection.
- **alternative:** Reuse an arbitrary prior index without widening the diff — rejected because it could omit changes introduced after that index's anchor. Keep exact-key-only lookup — rejected because the observed cache-publication race unnecessarily reruns every test.
- **design:** `../design.md#design-decision`
- **constitution:** §§I, II, III, V
- **trust:** ✓ verified by focused resolver/storage tests and a subprocess end-to-end merge-result test
