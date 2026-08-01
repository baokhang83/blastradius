# Session: Share pair selection between historical and mutation replay

- **intent:** Share pair selection between historical and mutation replay
- **started:** 2026-08-01

## Decision: Share selection and comparison after builds are established

- **where:** `validator/selection/PairSelectionAnalyzer`
- **why:** Historical and synthetic edges now use one dependency-to-decision and confirmed-failure comparison path, so an observed mutation miss means the same selection logic would miss it during history replay.
- **alternative:** Copy `RunCommand` selection code into mutation mode — rejected: copied logic can drift and would make the evidence weaker precisely where the feature is meant to increase trust.
- **design:** [class diagram](../design.md#class-diagram)
- **constitution:** §I, §III, §V
- **trust:** ✓ verified

## Knowledge transfer

The build scheduler remains separate from selection because history replay needs caching and concurrency, while mutation replay creates one new head at a time. Once each has a baseline dependency record and full-suite outcomes, however, their correctness question is identical: which tests would the selector choose for this edge, and did it omit a newly confirmed failure? Extracting just that seam preserves the mature historical scheduling behavior while eliminating duplicate verdict logic. The existing end-to-end history fixtures stayed green after the extraction, including the known would-miss case.
