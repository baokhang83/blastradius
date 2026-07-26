# Design: JSON selection report (#31)

started: 2026-07-26

## Class diagram

```mermaid
classDiagram
  class SelectMojo {
    +execute()
    -runTrack(CurrentChanges, IndexApplicability)
    -runSelect(CurrentChanges, IndexApplicability)
    -runFallback(CurrentChanges, IndexApplicability)
  }
  class CurrentChanges {
    +List~ChangedFile~ changedFiles()
  }
  class BuildReport {
    +int schemaVersion
    +Mode mode
    +Status indexApplicability
    +List~ChangedFile~ changedFiles
    +List~SelectionDecision~ decisions
    +int selectedCount
    +int skippedCount
    +int totalCount
    +Map~SelectionReason,int~ reasonCounts
    +Long estimatedTimeSavedMillis
    +TimingCoverage timingCoverage
  }
  class BuildReportWriter {
    +write(Path, BuildReport)
  }
  class TestTimingHistory {
    +int formatVersion
    +Map~TestIdentity,long~ durationMillisByTest
  }
  class TestTimingHistoryStore {
    +load(Path) TestTimingHistory
    +save(Path, TestTimingHistory)
  }
  class SurefireReportReader {
    +read(Path) Map~TestIdentity,long~
  }
  class TimingHistoryRecorder {
    +mojoSucceeded(ExecutionEvent)
    +mojoFailed(ExecutionEvent)
  }
  class TimingCoverage {
    +int recordedSkippedTests
    +int totalSkippedTests
  }
  class ChangedFile {
    +String path
    +FileKind kind
    +String changedClassName
  }
  class SelectionDecision {
    +TestIdentity test
    +boolean selected
    +SelectionReason reason
    +String matchedChangedClass
  }
  SelectMojo --> CurrentChanges : resolves
  SelectMojo --> BuildReport : creates from all modes
  SelectMojo --> BuildReportWriter : persists
  SelectMojo --> SurefireReportReader : imports prior test results
  SelectMojo --> TestTimingHistoryStore : maintains history
  SelectMojo --> TimingHistoryRecorder : registers
  TimingHistoryRecorder --> SurefireReportReader : reads completed reports
  BuildReport "1" *-- "*" ChangedFile : changedFiles
  BuildReport "1" *-- "*" SelectionDecision : decisions
  BuildReport --> TimingCoverage
  TestTimingHistoryStore --> TestTimingHistory
```

## Sequence: write one report for every selection mode

```mermaid
sequenceDiagram
  participant M as SelectMojo
  participant C as CurrentChangesResolver
  participant R as BuildReport
  participant W as BuildReportWriter
  participant S as Surefire report reader
  participant L as Maven execution listener
  participant H as test-timings.json
  participant F as .blastradius/last-build-report.json

  M->>C: resolve reactor root and base ref
  C-->>M: changedFiles and index applicability
  M->>H: load persisted timing history
  alt TRACK
    M->>M: run full suite and refresh index
  else SELECT
    M->>M: compute decisions and apply Surefire filter
  else FALLBACK
    M->>M: retain the full suite
  end
  M->>R: create schema v1 report from selection and timing history
  Note over R: Include all SelectionReason buckets, changed files,<br/>and a complete skipped-test duration estimate when available
  M->>W: write report
  W->>F: serialize JSON
  M->>L: register timing recorder
  Note over M,L: Selection completes before test execution
  M->>M: Maven runs Surefire or Failsafe
  L->>S: observe completion and read XML reports
  S-->>L: observed duration per test
  L->>H: merge and persist timing history
```

## Report contract

`BuildReport` remains the single source of truth; the console renderer continues to derive
its output from it. Every invocation writes schema version `1` at
`.blastradius/last-build-report.json`, with these stable fields:

```json
{
  "schemaVersion": 1,
  "mode": "SELECT",
  "indexApplicability": "APPLICABLE",
  "changedFiles": [
    { "path": "src/main/java/example/Foo.java", "kind": "JAVA_SOURCE", "changedClassName": "example.Foo" }
  ],
  "decisions": [
    { "test": { "className": "example.FooTest", "methodName": "works" }, "selected": true,
      "reason": "DEPENDENCY_MATCH", "matchedChangedClass": "example.Foo" }
  ],
  "selectedCount": 1,
  "skippedCount": 4,
  "totalCount": 5,
  "reasonCounts": {
    "DEPENDENCY_MATCH": 1,
    "FALLBACK_NON_SOURCE_CHANGE": 0,
    "NEW_OR_MODIFIED_TEST": 0,
    "NO_MATCH": 4
  },
  "estimatedTimeSavedMillis": 2430,
  "timingCoverage": { "recordedSkippedTests": 4, "totalSkippedTests": 4 }
}
```

`selectedCount` is the test set the goal tells Surefire/Failsafe to run; `skippedCount` is
the remaining discovered set. The goal executes before the test engines, so the report does not
claim final execution results. At the beginning of each build, the goal loads the versioned
per-test durations in `.blastradius/test-timings.json`. A Maven execution listener registered by
the goal observes Surefire/Failsafe completion, reads those standard XML reports, and merges the
fresh durations immediately - even when the build began with `clean`. For a SELECT build,
`estimatedTimeSavedMillis` is the sum of the persisted durations for every skipped test. It is
`null` until every skipped test has timing history; `timingCoverage` makes that status
machine-readable. TRACK and FALLBACK report zero skipped tests and zero estimated savings. All four
`SelectionReason` keys appear even when a mode has no per-test decisions, so CI consumers do not
need mode-specific missing-key handling.

## Key choice

The report extends the existing `BuildReport` rather than adding a parallel report model. That
keeps JSON, console rendering, and selection behavior tied to one immutable value and preserves
the existing output fields. A small versioned timing-history file is the second durable artifact:
it contains only test identity and last observed duration, is updated from Surefire/Failsafe's
standard XML reports immediately after the test engine completes, and remains useful when CI
restores `.blastradius/` as a cache. A listener injected into the test runtime was rejected
because it would add a second execution integration and risk changing the build; a synthetic
equal-cost percentage was rejected because it is not the milliseconds estimate requested here.
