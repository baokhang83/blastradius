# Design: core dynamic dependency tracking and test-selection engine

started: 2026-07-12

> ✓ **Backfilled, then reviewed.** `blastradius-core` shipped in the initial commit without going
> through the loop. This design was reconstructed from the code, the validator's Phase-0 ADR
> (`specs/001-shadow-mode-validator/research.md`), and the constitution — not authored live — then
> confirmed by the maintainer on 2026-07-12. Diagrams reflect the engine as it shipped. See the
> session journal for the decisions and their (now ✓) trust markers.

## Class diagram

```mermaid
classDiagram
  class DependencyTrackingAgent {
    +premain(args, Instrumentation)
    +transform(...) byte[]
  }
  class TestBoundaryListener {
    +currentTest() TestIdentity
  }
  class DependencyRecordWriter
  class DependencyRecordReader
  class TestIdentity

  class ChangedFileClassifier {
    +classify(repo, base, head) List~ChangedFile~
  }
  class ChangedFile
  class FileKind

  class SelectionEngine {
    +selectAll(...) List~SelectionDecision~
  }
  class FallbackSelector {
    +shouldFallback(changed) boolean
  }
  class DependencyMatchSelector
  class NewOrModifiedTestSelector
  class SelectionDecision
  class SelectionReason

  DependencyTrackingAgent ..> TestBoundaryListener : attributes load to current test
  DependencyTrackingAgent ..> DependencyRecordWriter : writes per-test class checksums
  ChangedFileClassifier --> ChangedFile
  ChangedFile --> FileKind
  SelectionEngine --> FallbackSelector
  SelectionEngine --> DependencyMatchSelector
  SelectionEngine --> NewOrModifiedTestSelector
  SelectionEngine ..> DependencyRecordReader : reads prior dependencies
  SelectionEngine --> SelectionDecision
  SelectionDecision --> SelectionReason
```

## Sequence: selecting tests for one commit pair

```mermaid
sequenceDiagram
  participant Caller
  participant Engine as SelectionEngine
  participant Fallback as FallbackSelector
  participant Match as DependencyMatchSelector
  Caller->>Engine: selectAll(allTests, deps, newOrModified, changedFiles)
  Engine->>Fallback: shouldFallback(changedFiles)?
  alt a NON_SOURCE file changed
    Fallback-->>Engine: true
    Engine-->>Caller: select ALL (FALLBACK_NON_SOURCE_CHANGE)
  else only JAVA_SOURCE / INERT changes
    loop per test
      alt test is new or its own file changed
        Engine-->>Caller: select (NEW_OR_MODIFIED_TEST)
      else
        Engine->>Match: deps ∩ changedClassNames?
        Match-->>Engine: DEPENDENCY_MATCH or NO_MATCH
      end
    end
  end
```
