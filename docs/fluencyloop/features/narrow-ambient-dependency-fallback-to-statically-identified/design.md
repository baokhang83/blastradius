# Design: attribute discovery-loaded classes by runtime execution

started: 2026-07-27

## Class diagram

```mermaid
classDiagram
  class TestBoundaryListener {
    +executionStarted()
  }
  class DependencyTrackingAgent {
    +snapshotAmbientDependencies()
    +recordAmbientExecution()
  }
  class AmbientClassInstrumenter {
    +instrument runtime use probes
  }
  class Instrumentation {
    +retransformClasses()
  }
  class DependencyRecordWriter {
    +write()
  }
  class DependencyIndex {
    +testDependencies
    +ambientDependencies
  }
  TestBoundaryListener --> DependencyTrackingAgent : opens test boundary
  DependencyTrackingAgent --> Instrumentation : retransforms project classes
  DependencyTrackingAgent --> AmbientClassInstrumenter : adds method-entry probe
  AmbientClassInstrumenter --> DependencyTrackingAgent : runtime execution callback
  DependencyTrackingAgent --> DependencyRecordWriter : persists attributed dependencies
  DependencyRecordWriter --> DependencyIndex : builds selection baseline
```

## Sequence: discovery-loaded application class

```mermaid
sequenceDiagram
  participant JUnit as JUnit discovery
  participant Agent as tracking agent
  participant Listener as test boundary listener
  participant Class as already-loaded application class
  participant Index as dependency index

  JUnit->>Agent: load application class before any test
  Listener->>Agent: first test starts
  Agent->>Agent: snapshot ambient classes
  Agent->>Class: retransform with runtime use probes
  Note over Listener,Agent: test identity becomes current after retransformation
  Class->>Agent: method, field, type, or class-literal use during test
  Agent->>Agent: record class under current test
  Agent->>Index: persist per-test dependency
  alt class cannot be retransformed or instrumented
    Agent->>Index: retain class as ambient
  end
```

## Decision

Replace the static test-class scan with runtime attribution. At the first test boundary, the
agent retransforms discovery-loaded project classes and adds probes for method execution plus
field, type, and class-literal use. If a test later uses one of those classes, the probe records
the class under that test's normal dependency entry. Selection can then use the existing
dependency matcher, including indirect calls, reflection, dependency-injection paths, and direct
non-constant static-field reads that a static scan cannot see.

The ambient set remains the conservative escape hatch only for classes that cannot be safely
retransformed or instrumented. Those classes still trigger the existing full-scope fallback when
they change. The agent must instrument only project class-output directories, never JDK or third
party library classes, to avoid changing platform behavior and to keep the retransformation scope
bounded.

The rejected alternative is a compiled-test-class reference scan. It can identify direct type
references but cannot prove an indirect or dynamic runtime path, so it could miss the very tests
the ambient guard was introduced to protect.
