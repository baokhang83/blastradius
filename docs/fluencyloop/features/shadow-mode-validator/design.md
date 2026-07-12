# Design: shadow-mode test-selection validator

started: 2026-07-12

> ✓ **Backfilled, then reviewed.** `blastradius-validator` shipped from
> `specs/001-shadow-mode-validator/` without going through the loop. This design was
> reconstructed from that spec/plan/`research.md`/contracts and the code — not authored live —
> then confirmed by the maintainer on 2026-07-12. See the session journal for the decisions and
> their (now ✓) trust markers.

## What it is

The validator measures whether the selection engine is *sound* by **replaying real project
history**: for each commit pair it runs the target's own build to get ground truth, runs the
engine's selection, and reports any test the engine would have skipped that actually failed
(a "would-miss"). It never mutates the target repo.

## Class diagram

```mermaid
classDiagram
  class RunCommand {
    +run(RunConfig) int
  }
  class CommitWindowResolver
  class CommitPair
  class CommitCheckout {
    +checkoutCommit(sha) Path
  }
  class MavenBuildRunner {
    +run(dir, agentJar, out) BuildResult
    +runSingleTest(dir, test) BuildResult
  }
  class SurefireReportParser
  class GroundTruthResolver {
    +resolve(...) GroundTruthResult
  }
  class GroundTruthResult
  class SelectionEngine
  class VerdictCalculator {
    +verdict(selected, groundTruth) Verdict
  }
  class WouldMissCase
  class AnalysisReport
  class ReportWriter {
    +write(report, path)
  }
  class TextSummaryRenderer

  RunCommand --> CommitWindowResolver
  RunCommand --> CommitCheckout
  RunCommand --> GroundTruthResolver
  RunCommand --> VerdictCalculator
  RunCommand --> ReportWriter
  CommitWindowResolver --> CommitPair
  GroundTruthResolver --> MavenBuildRunner
  GroundTruthResolver --> SurefireReportParser
  GroundTruthResolver --> GroundTruthResult
  VerdictCalculator ..> SelectionEngine : compares selection vs
  VerdictCalculator --> WouldMissCase
  ReportWriter --> AnalysisReport
  TextSummaryRenderer ..> AnalysisReport : renders
```

## Sequence: validating one commit pair

```mermaid
sequenceDiagram
  participant Run as RunCommand
  participant Git as CommitCheckout
  participant Maven as MavenBuildRunner
  participant GT as GroundTruthResolver
  participant Engine as SelectionEngine
  participant V as VerdictCalculator
  Run->>Git: checkout base commit (scratch copy, JGit)
  Run->>Maven: mvn clean test + agent via JAVA_TOOL_OPTIONS
  Maven-->>GT: Surefire/Failsafe XML + recorded deps
  GT-->>Run: GroundTruthResult (pass / confirmed-fail / flaky)
  Run->>Git: checkout head commit
  Run->>Engine: selectAll(changed files, tracked deps)
  Engine-->>V: per-test SelectionDecision
  V->>V: any selected=NO but actually FAILED? -> WouldMissCase
  V-->>Run: Verdict
  Run->>Run: aggregate -> AnalysisReport (JSON) + text summary
```
