# Session: Replay synthetic mutations within every historical pair

- **intent:** Replay synthetic mutations within every historical pair
- **started:** 2026-08-02

## Decision: Attach mutants to real pair context instead of a standalone command

- **where:** `RunCommand, HistoricalMutationValidator, AnalysisReport`
- **why:** Each mutant is a child of H, its killers are established against H, and its selected tests are evaluated across B to M so controlled faults validate the same historical replay context.
- **alternative:** Keep the standalone mutate command — rejected: it uses one current HEAD baseline and cannot measure the 300-pair replay the validator reports.
- **design:** ../design.md#sequence-one-historical-pair-with-mutation-validation
- **constitution:** §III, §V
- **trust:** ✓ verified

## Knowledge transfer

- **HistoricalMutationValidator:** restores each real head in a disposable validator checkout, commits a controlled child mutant, and never alters the operator's target working tree, status: documented.
- **Oracle split:** head outcomes establish whether a test was healthy before mutation, while base dependencies and the B-to-M diff determine whether the historical selector chose it, status: documented.
- **Report boundary:** `AnalysisReport` includes optional mutation evidence and its verdict joins historical would-miss cases with skipped confirmed mutation killers, status: documented.
