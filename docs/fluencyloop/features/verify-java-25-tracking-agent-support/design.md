# Design: Verify Java 25 tracking agent support

<!--
FluencyLoop Stage 2 — one design.md per feature, committed alongside it.
Defaults: a class diagram and a sequence diagram (the two first-class Mermaid types that
pay their way most often). Add an interaction/flow view only when it earns its place.
Keep the Mermaid blocks TOP-LEVEL (not nested in another code fence) so GitHub renders them.
Delete this comment once the diagrams are real.
-->

started: 2026-07-20

## Class diagram

```mermaid
classDiagram
  class TestBoundaryListener {
    -InheritableThreadLocal~TestIdentity~ CURRENT_TEST
    +currentTest() TestIdentity
    +executionStarted(TestIdentifier)
    +executionFinished(TestIdentifier, TestExecutionResult)
  }
  class DependencyTrackingAgent {
    +transform(Module, ClassLoader, String, Class, ProtectionDomain, byte[]) byte[]
    +transform(ClassLoader, String, Class, ProtectionDomain, byte[]) byte[]
    +recordedDependencies() Map
  }
  class VirtualThread {
    inherits "test identity at creation"
  }
  class SealedDependency
  class HiddenTemplate
  class DependencyRecordWriter

  TestBoundaryListener --> VirtualThread : propagates TestIdentity
  VirtualThread --> DependencyTrackingAgent : loads classes
  DependencyTrackingAgent --> DependencyRecordWriter : writes test -> class checksum
  VirtualThread --> SealedDependency : named class load
  VirtualThread --> HiddenTemplate : hidden definition source
```

## Sequence: JDK 25 class-load attribution

```mermaid
sequenceDiagram
  participant JUnit as JUnit test thread
  participant Listener as TestBoundaryListener
  participant VT as virtual thread
  participant Agent as DependencyTrackingAgent
  participant Record as dependency record

  JUnit->>Listener: executionStarted(test)
  Listener->>Listener: set TestIdentity
  JUnit->>VT: Thread.startVirtualThread(work)
  Note over Listener,VT: identity is copied when the child is created
  VT->>Agent: module-aware class-load callback
  Agent->>Agent: normalize stable class name and checksum bytecode
  Agent->>Record: test -> dependency
  VT-->>JUnit: join
  JUnit->>Listener: executionFinished(test)
  Listener->>Listener: clear parent identity
```

## Compatibility boundaries

- The test uses a sealed production type and `Lookup#defineHiddenClass` from a virtual
  thread on JDK 25. Hidden classes do not enter the class-load transformer, so the
  listener compares the agent's loaded-class list at test start and finish. New hidden
  classes are recorded under their stable source name, never their synthetic runtime
  name, because selection matches changed source-class names.
- The agent remains observation-only and returns `null`; it does not open modules or
  rewrite bytecode. The JDK's module-aware `ClassFileTransformer` entry point is tested
  directly as well as exercised by the subprocess integration test.
- Identity is inherited only when a child thread is created. Tests must still await
  asynchronous work before finishing, otherwise any tracker cannot reliably assign
  late work to a completed test.

## Validation

1. Start a real Maven fixture under the built `-javaagent` on JDK 25.
2. In one JUnit test, load a sealed implementation and define a hidden class from a
   virtual thread; assert both stable source names are recorded for that test.
3. Unit-test the module-bearing transformer callback and preserve the existing
   null-name behavior for unnameable definitions.
4. Run Maven's full verify lifecycle on JDK 25, then Gradle's complete check.
