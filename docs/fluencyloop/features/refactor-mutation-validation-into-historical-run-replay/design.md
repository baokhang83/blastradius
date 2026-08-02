# Design: Refactor mutation validation into historical run replay

started: 2026-08-02

## Purpose

`run` remains the sole validator action. Its normal historical replay is unchanged unless the
operator enables `--mutation-validation`. When enabled, every eligible real sequential pair
`B -> H` receives a bounded set of synthetic mutants made from `H`. Each mutant `M` is a direct
child of `H`, but selection is evaluated across `B -> M`: this preserves the real pair's
dependency baseline and its accumulated change set, then adds one controlled fault at the head.

The full suite at `H` is the mutation baseline. A test counts as a mutation killer only when it
passes at `H` and is confirmed failed at `M`. This excludes failures already introduced by the
real historical change.

## Class diagram

```mermaid
classDiagram
  class Main {
    +run(args)
  }
  class RunConfig {
    +mutationValidation
    +maxMutationClassesPerPair
    +maxMutationsPerPair
    +mutationTimeLimitMinutes
  }
  class RunCommand {
    +run(config)
    -analyzeWindow()
  }
  class HistoricalMutationValidator {
    +validate(pair, baseBuild, headBuild, checkout)
  }
  class MutationCandidateGenerator {
    +generate(headTree, limits)
  }
  class CommitCheckout {
    +checkoutCommit(sha)
    +commitFile(path, contents, message)
  }
  class PairSelectionAnalyzer {
    +analyze(baseToMutant, baseDependencies, baseOutcomes, mutantOutcomes)
  }
  class MutationValidationReport {
    +coverage
    +experiments
  }
  class AnalysisReport {
    +wouldMissCases
    +mutationValidation
    +verdict
  }

  Main --> RunConfig
  Main --> RunCommand
  RunCommand --> HistoricalMutationValidator
  RunCommand --> AnalysisReport
  HistoricalMutationValidator --> MutationCandidateGenerator
  HistoricalMutationValidator --> CommitCheckout
  HistoricalMutationValidator --> PairSelectionAnalyzer
  HistoricalMutationValidator --> MutationValidationReport
  AnalysisReport --> MutationValidationReport
```

## Sequence: one historical pair with mutation validation

```mermaid
sequenceDiagram
  participant R as RunCommand
  participant C as CommitBuild cache
  participant W as CheckoutPool clone
  participant M as HistoricalMutationValidator
  participant S as PairSelectionAnalyzer
  participant O as AnalysisReport

  R->>C: load B tracked build and H full-suite build
  R->>S: compare real B to H selection and ground truth
  alt mutation validation enabled and pair is analyzable
    R->>W: checkout H
    R->>M: validate B, H, and mutation limits
    M->>M: generate deterministic candidates from H
    loop each bounded candidate
      M->>W: restore H, apply candidate, commit direct child M
      M->>W: run full suite on M and confirm failures
      M->>S: select across B to M using B dependencies
      M->>M: retain killers that passed at H and failed at M
    end
    M-->>R: experiments and aggregate coverage
  end
  R->>O: attach mutation report and combine verdicts
```

## Contract

- `mutate` and its separate JSON/text report are removed. `run` gains
  `--mutation-validation`, `--max-mutation-classes-per-pair`,
  `--max-mutations-per-pair`, and `--mutation-time-limit-minutes`.
- Mutation validation is opt-in. Bounds are explicit: class and mutation limits apply to each
  historical pair, while the time limit bounds the complete run.
- Every synthetic mutant is isolated in a validator checkout. The target repository working tree
  is never altered.
- Unbuildable mutants, flaky failures, and tests already failing at `H` remain visible but do not
  create soundness failures. A confirmed killer that Blastradius did not select makes both the
  mutation result and the overall historical run fail.
- The source-of-truth `AnalysisReport` owns a nullable `MutationValidationReport`. It carries the
  full denominator and every experiment tagged with its real `B -> H` pair and synthetic mutant
  SHA.

## Design choice to confirm

The recommended selection edge is `B -> M`, where `M` is `H` plus one mutation. It tests the
same dependency baseline and historical change context as the real `B -> H` pair. The rejected
alternative is selecting only across `H -> M`; that would rebaseline at every head and turn this
into many standalone diagnostics, no longer validating the replayed historical pair.
