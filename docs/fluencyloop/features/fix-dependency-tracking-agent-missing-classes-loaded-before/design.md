# Design: fix dependency-tracking agent missing classes loaded before a test's tracking window opens

started: 2026-07-26

## Bug (confirmed by local repro)

`DependencyTrackingAgent.transform()` is a plain `ClassFileTransformer`, registered without
`canRetransform`, so the JVM invokes it exactly once per class — on that class's first load in
the fork. JUnit Platform's discovery pass (building the `TestPlan` from every test class's
methods/annotations) happens before `TestBoundaryListener.executionStarted` ever fires for the
first test, and can force-load a class like `ChangedFile` during that pass. Once loaded, it is
never re-transformed, so it is permanently invisible to the tracker for the rest of the fork —
even for tests that construct/use it directly and run later. Verified locally: 0 of 217 recorded
test executions in blastradius-core's freshly-tracked index list `ChangedFile` as a dependency,
despite three tests (`ChangedFileClassifierTest`, `SelectionEngineTest`, `FallbackSelectorTest`)
constructing it directly.

## Class diagram

```mermaid
classDiagram
  class DependencyTrackingAgent {
    -checksumsByTest: Map~TestIdentity, Map~
    -ambientDependencies: Set~String~
    +transform(loader, className, ...) byte[]
    +recordedDependencies() Map
    +ambientDependencies() Set~String~
  }
  class TestBoundaryListener {
    +executionStarted(TestIdentifier)
    +executionFinished(TestIdentifier, Result)
    -firstTestSeen: boolean
  }
  class DependencyIndex {
    +anchorCommit: String
    +testDependencies: List~TestDependencyEntry~
    +ambientDependencies: Set~String~
  }
  class FallbackSelector {
    +shouldFallback(changedFiles) boolean
  }
  class AmbientDependencySelector {
    +selectAll(changedClassNames, ambientDependencies) boolean
  }
  class SelectionEngine {
    +selectAll(...) List~SelectionDecision~
  }
  TestBoundaryListener --> DependencyTrackingAgent : snapshots pre-first-test\nloaded classes once
  DependencyTrackingAgent --> DependencyIndex : ambientDependencies
  SelectionEngine --> FallbackSelector : non-source change?
  SelectionEngine --> AmbientDependencySelector : changed class is ambient?
  AmbientDependencySelector --> DependencyIndex : reads ambientDependencies
```

## Sequence: before (bug) vs after (fix)

```mermaid
sequenceDiagram
  participant JVM as JVM/Surefire fork
  participant Disc as JUnit discovery
  participant Agent as DependencyTrackingAgent
  participant T1 as ChangedFileClassifierTest
  Note over JVM,T1: BEFORE — the bug
  JVM->>Disc: build TestPlan (scans all test classes)
  Disc->>Agent: loads ChangedFile (transform() fires once)
  Agent-->>Agent: currentTest() == null -> discarded
  Disc->>T1: executionStarted
  T1->>T1: new ChangedFile(...) (already loaded, no transform() call)
  T1->>Agent: executionFinished
  Note over Agent: ChangedFile never recorded for ANY test
```

```mermaid
sequenceDiagram
  participant JVM as JVM/Surefire fork
  participant Disc as JUnit discovery
  participant Agent as DependencyTrackingAgent
  participant TBL as TestBoundaryListener
  participant T1 as ChangedFileClassifierTest
  Note over JVM,T1: AFTER — the fix
  JVM->>Disc: build TestPlan (scans all test classes)
  Disc->>Agent: loads ChangedFile (transform() fires once, discarded)
  Disc->>TBL: executionStarted (first test in this fork)
  TBL->>Agent: snapshot loadedClasses() once -> ambientDependencies
  Agent-->>Agent: ambientDependencies += ChangedFile
  TBL->>T1: proceed
  T1->>T1: new ChangedFile(...)
  Note over Agent: index now carries ambientDependencies alongside per-test data
  Note over Agent: SELECT: ChangedFile changed and in ambientDependencies -> fallback (safe)
```

## Rejected alternative

Retransform every already-loaded class at each `executionStarted` (`canRetransform=true` +
`Instrumentation.retransformClasses`) so `transform()` fires again, attributed to whichever test
is current. Rejected: it only reassigns the "first loader wins" problem to whichever test happens
to run first after the retransform, and re-running it per test is expensive across a large
classpath. The ambient-set approach captures the pre-discovery loaded-classes snapshot exactly
once per fork and treats it as fork-wide (a changed ambient class conservatively triggers
fallback), which is cheap and matches the system's existing philosophy of falling back rather
than guessing (FR-007) instead of trying to attribute what is structurally unattributable per-test.
