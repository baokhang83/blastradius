# Session: Define opt-in historical mutation bounds

- **intent:** Define opt-in historical mutation bounds
- **started:** 2026-08-02

## Decision: Scope mutation limits to each replayed pair

- **where:** `validator/mutation/MutationValidationConfig`
- **why:** Per-pair class and mutation caps make the cost of a 300-pair replay explicit, while one time limit bounds the complete launch.
- **alternative:** Reuse the standalone command's generic max-class and max-mutation names — rejected: they hide whether a limit applies per pair or globally.
- **design:** ../design.md#contract
- **constitution:** §II, §III
- **trust:** ✓ verified

## Knowledge transfer

- **MutationValidationConfig:** is present only when historical mutation replay is requested; its class and candidate limits reset for each eligible pair, while its time limit governs the whole invocation, status: documented.
- **Cost boundary:** a 300-pair run is bounded by explicit work per pair and by elapsed time, instead of inheriting the standalone diagnostic's one-baseline assumptions, status: documented.
