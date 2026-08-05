# Design: core: attribute @BeforeAll-loaded classes to their container's tests

started: 2026-08-05
branch: feature/219-core-attribute-beforeall-loaded-classes-to-their-contain
relates to: #41 (the coverage-gap warning this closes), gh #219

## Problem

`TestBoundaryListener` only publishes a `currentTest()` identity while a JUnit 5 **test method**
is running (`executionStarted`/`executionFinished` guarded by `testIdentifier.isTest()`). A class
loaded only inside `@BeforeAll` runs while no test is current, so `DependencyTrackingAgent` either
drops the load entirely or folds it into the fork-wide ambient set — invisible to any specific
test's attribution. README's Known limitations documents this; #41 only warns about it. This
closes it: attribute the load, instead of just flagging it.

## Class diagram

```mermaid
classDiagram
    class TestBoundaryListener {
        -CLASSES_AT_TEST_START Set~Class~
        -CONTAINER_STACK Deque~ContainerFrame~
        +currentTest() TestIdentity
        +executionStarted(TestIdentifier)
        +executionFinished(TestIdentifier, TestExecutionResult)
        -toTestIdentity(TestIdentifier) TestIdentity
        -toContainerIdentity(TestIdentifier) Optional~TestIdentity~
    }
    class ContainerFrame {
        +identity TestIdentity
        +classesAtStart Set~Class~
        +memberTests Set~TestIdentity~
        +beforeAllWindowClosed boolean
    }
    class TestExecutionContext {
        -CURRENT_TEST InheritableThreadLocal~TestIdentity~
        +currentTest() TestIdentity
        +start(TestIdentity)
        +finish()
    }
    class TestIdentity {
        +className String
        +methodName String
        +baselineKey() TestIdentity
    }
    class DependencyTrackingAgent {
        -checksumsByTest Map~TestIdentity, Map~
        +recordTestStarted(TestIdentity)
        +recordHiddenClassesLoadedSince(TestIdentity, Set~Class~)
        +unionContainerDependencies(TestIdentity, Set~TestIdentity~)
        +recordedDependencies() Map
    }
    TestBoundaryListener o-- ContainerFrame : pushes per class nesting
    TestBoundaryListener --> TestExecutionContext : publishes current identity
    TestBoundaryListener --> DependencyTrackingAgent : records boundaries
    TestExecutionContext --> TestIdentity
    DependencyTrackingAgent --> TestIdentity : keys checksumsByTest
    ContainerFrame --> TestIdentity : identity, memberTests
```

The only new type is `ContainerFrame`, a private record `TestBoundaryListener` stacks per class
nesting level (so `@Nested` classes fall out for free). `TestIdentity` already reserves a
`null` method name as a "class-level identity" per its own Javadoc — reused here as the
synthetic container identity, not invented.

## Sequence: one class, two tests, a @BeforeAll dependency

```mermaid
sequenceDiagram
    participant JUnit as JUnit Platform
    participant Listener as TestBoundaryListener
    participant Ctx as TestExecutionContext
    participant Agent as DependencyTrackingAgent

    JUnit->>Listener: executionStarted(FooTest container)
    Listener->>Agent: recordAmbientSnapshot()
    Listener->>Ctx: start(FooTest identity)
    Listener->>Listener: push ContainerFrame(classesAtStart)
    Note over JUnit: runs @BeforeAll, loads Helper
    JUnit->>Agent: transform(Helper) - attributed to container identity
    JUnit->>Listener: executionStarted(test1)
    Listener->>Agent: recordHiddenClassesLoadedSince(container) - closes BeforeAll window
    Listener->>Listener: frame.memberTests.add(test1)
    Listener->>Ctx: start(test1)
    Note over JUnit: test1 body runs
    JUnit->>Listener: executionFinished(test1)
    Listener->>Ctx: finish()
    JUnit->>Listener: executionStarted(test2)
    Listener->>Listener: frame.memberTests.add(test2)
    Listener->>Ctx: start(test2)
    Note over JUnit: test2 body runs
    JUnit->>Listener: executionFinished(test2)
    Listener->>Ctx: finish()
    JUnit->>Listener: executionFinished(FooTest container)
    Listener->>Listener: pop frame
    Listener->>Agent: unionContainerDependencies(container, memberTests)
    Note over Agent: Helper moves into test1 and test2, container entry dropped
```

The named-class path needs no `DependencyTrackingAgent.transform()` change: it already attributes
every load to whatever `currentTest()` returns, and now that can be the container identity while
`@BeforeAll` runs. Only the hidden-class window-close and the final union are new agent surface
(`unionContainerDependencies`), and `recordHiddenClassesLoadedSince` is reused as-is.

## Decisions

- **Close the @BeforeAll hidden-class window at the first child test, not at container finish.**
  The named-class map for the container key naturally spans only `@BeforeAll` (`currentTest()`
  moves to the first test the moment it starts). The hidden-class diff has no such natural
  boundary — closing it at container finish instead would span the whole class, including every
  test body, leaking test-only hidden/lambda classes into sibling tests. Safe (extra deps only
  widen re-runs, never hide one needed) but noisy; closing it early keeps the container's window
  precisely `@BeforeAll`-shaped.
- **Stack container frames instead of one field.** `@Nested` classes fire their own container
  start/finish nested inside the outer one; a stack makes a test join its *immediate* container's
  `memberTests` for free. Not handled: an outer class's own `@BeforeAll` dependencies do not
  propagate down to a `@Nested` class's tests — out of this issue's scope, same shape as the
  existing `@AfterAll` gap (untouched by this feature).
- **Scoped to `@BeforeAll` only, `@AfterAll` stays open — confirmed with the developer.**
  `TestExecutionListener` gives no hook between "last child test finished" and
  `executionFinished(container)` (which fires after `@AfterAll` runs), so closing `@AfterAll` too
  would mean re-arming `currentTest = container` after *every* test finishes rather than only
  before the first — making the container a standing/background identity for the whole class.
  That also sweeps up inter-test `@BeforeEach`/`@AfterEach` gaps and unions them into *every*
  sibling test at container finish, not just the adjacent one: safe (over-attribution, never
  under-), but coarser than this feature's `@BeforeAll`-only precision. Deliberately left as a
  documented, separate gap rather than folded in — tracked as #221.
