# Design: attribute a project class's runtime use to every test that exercises an already-loaded instance, not just the test whose window first loaded it

started: 2026-07-28

**Problem:** on real apache/shenyu, a change to `ConfigsServiceImpl.java` selected 0 of
3627 tests. Root cause: it's a Spring-managed bean, instantiated once when the *first*
test class needing that `ApplicationContext` runs — Spring then caches and reuses that
context for every later test class with the same config. The agent's `transform()` only
fires once per class-load, so only the first test that happened to trigger the load ever
got attributed. Not Spring-specific: true of any singleton/cached/pooled project class.

**Fix:** `AmbientClassInstrumenter` already injects a runtime-use callback into a class's
bytecode — today wired up only for the one-time, pre-first-test "ambient snapshot". This
generalizes it to run from `transform()`'s normal path, for every project class, the
first time it loads, whether or not a test is currently running — so a later test that
reuses an already-loaded instance still gets attributed via the injected callback.

Full walkthrough (with the observed-bug context and the four decisions below) was shown
as an Artifact during design review; this file is the durable copy of the two diagrams.

## Class diagram

```mermaid
classDiagram
    class DependencyTrackingAgent {
      -Map~TestIdentity, Map~String,String~~ checksumsByTest
      -Set~String~ ambientDependencies
      -Map~String,String~ ambientChecksums
      -AmbientClassInstrumenter ambientClassInstrumenter
      +transform(loader, className, classBeingRedefined, protectionDomain, bytes) byte[]
      +recordAmbientExecution(className) void
      -isProjectClass(className, protectionDomain) boolean
    }
    class AmbientClassInstrumenter {
      +instrument(className, bytecode) byte[]
    }
    class TestBoundaryListener {
      +currentTest() TestIdentity
    }
    class AmbientDependencySelector {
      +shouldFallback(changedClasses, ambient) boolean
    }
    DependencyTrackingAgent --> AmbientClassInstrumenter : NEW — every project class,\nnot just the pre-first-test snapshot
    DependencyTrackingAgent --> TestBoundaryListener : "which test is running right now?"
    AmbientDependencySelector --> DependencyTrackingAgent : reads ambientDependencies()\n(shrinks as more classes get instrumented)
```

## Sequence: two tests, one cached Spring context

```mermaid
sequenceDiagram
    participant JVM
    participant Agent as DependencyTrackingAgent
    participant Inst as AmbientClassInstrumenter
    participant Bean as ConfigsServiceImpl instance
    participant TestA as ConfigsServiceImplTest
    participant TestB as ConfigsExportImportControllerTest

    TestA->>JVM: starts, builds Spring context (cold)
    JVM->>Agent: transform("ConfigsServiceImpl", bytes)
    Agent->>Agent: isProjectClass = true
    Agent->>Inst: instrument(bytes)
    Inst-->>Agent: instrumented bytes (+callback)
    Agent->>Agent: ambientChecksums.put(class, sha256)
    Agent->>Agent: checksumsByTest[TestA].put(class, sha256)
    Agent-->>JVM: return instrumented bytes
    JVM->>Bean: defines class from instrumented bytes
    TestA->>Bean: exercises the bean directly

    Note over TestA,TestB: Spring caches the context by config signature.<br/>TestB reuses the SAME bean instance — no reload,<br/>no second transform() call.

    TestB->>Bean: calls a service method (via ConfigsService)
    Bean->>Agent: recordAmbientExecution("ConfigsServiceImpl")
    Agent->>Agent: currentTest = TestB, checksum found in ambientChecksums
    Agent->>Agent: checksumsByTest[TestB].putIfAbsent(class, sha256)
    Note right of Agent: Before this fix: no callback existed on Bean,<br/>so TestB's dependency map never<br/>recorded ConfigsServiceImpl at all.
```
