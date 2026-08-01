# Session: Run bounded mutation experiments and report confirmed misses

- **intent:** Run bounded mutation experiments and report confirmed misses
- **started:** 2026-08-01

## Decision: Require a confirmed full-suite outcome before judging selection

- **where:** `validator/mutation/MutationCommand` and mutation report types
- **why:** A test counts as killing only when it passed at baseline and is a confirmed failure on the synthetic child, so a validator miss has an observable regression behind it rather than a guess.
- **alternative:** Treat every mutant build failure or first failing test as evidence — rejected: compilation failures and flakes cannot establish that a selected test would have detected the mutation.
- **design:** [operator contract](../design.md#operator-contract)
- **constitution:** §I, §III, §IV, §V
- **trust:** ✓ verified

## Knowledge transfer

The mutation command is opt-in, but `--mutation-class` is not required. Without it, the runner scans deterministic production-source order and stops at the configured class, mutation, and time bounds; the class option is an exact filter for a focused investigation. Baseline dependencies are recorded once with the tracking agent, while each synthetic child is judged by an agent-free full-suite run. The report then shows the whole denominator, including unbuildable mutations, existing baseline failures, flakes, confirmed killing tests, and selected versus skipped killing tests. The two fixture integrations prove both outcomes: a normal tracked dependency passes, while the known `@BeforeAll` gap surfaces as a failed mutation verdict.
