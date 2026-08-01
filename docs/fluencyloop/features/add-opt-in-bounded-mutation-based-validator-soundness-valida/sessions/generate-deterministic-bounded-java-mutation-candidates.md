# Session: Generate deterministic, bounded Java mutation candidates

- **intent:** Generate deterministic, bounded Java mutation candidates
- **started:** 2026-08-01

## Decision: Enumerate a narrow token-preserving corpus deterministically

- **where:** `blastradius-validator/.../mutation/MutationCandidateGenerator`
- **why:** Boolean and comparison-token inversions give the first mutation corpus meaningful, reproducible defects without adding a parser framework or making historical validator runs expensive.
- **alternative:** Integrate a general mutation-testing engine or mutate arbitrary Java syntax — rejected: that would broaden the feature beyond a controlled soundness corpus and obscure why a candidate exists.
- **design:** [operator contract](../design.md#operator-contract)
- **constitution:** §II, §IV, §V
- **trust:** ✓ verified

## Knowledge transfer

The generator is deliberately not deciding whether a mutation is valuable or whether it will be killed. It only produces a stable list of small edits, then later slices let the target project's compiler and full test suite be the authorities. Skipping comments, strings, characters, and text blocks matters because changing explanatory text would create a misleading mutation that has no executable behavior. Applying limits only after sorting gives an operator reproducible evidence: the same project revision and limits select the same starting corpus.
