# Session: scaffold the Gradle adapter and prove SELECT filtering in a real consumer build

- **intent:** scaffold the Gradle adapter and prove SELECT filtering in a real consumer build
- **started:** 2026-07-20

## Decision: Compute Gradle selection immediately before the Test task executes

- **where:** `blastradius-gradle-plugin GradleSelectAction and BlastradiusPlugin`
- **why:** Test discovery needs compiled test classes, so selection runs after testClasses but before Gradle starts the test JVM; invalid or missing indexes leave the task unfiltered.
- **alternative:** Configure a separate selection task during Gradle configuration — deferred because it requires configuration-cache-safe task inputs and belongs to issue #19.
- **design:** ../design.md#select-flow
- **constitution:** §III
- **trust:** ✓ verified

## Knowledge transfer

`java-gradle-plugin` generates the descriptor that maps the consumer-facing plugin id
`io.github.baokhang83.blastradius` to `BlastradiusPlugin`; it also supplies Gradle TestKit.
The repository now has a Gradle build that treats the existing `blastradius-core` Java sources
as a shared project dependency, so the adapter reuses the established selection engine rather
than maintaining a second implementation.

`BlastradiusExtension` is the Gradle configuration surface. Its required `baseRef` and default
`.blastradius/index.json` match Maven's concepts. `GradleSelectAction` resolves both at the
root project, preserves the path-boundary validation, and only applies a filter when the index
is readable, anchored in reachable Git history, and the current commit is not the base ref.
Every other case leaves Gradle unfiltered, preserving the full-suite safety default.

Gradle TestKit runs a real temporary consumer build. The fixture creates a Git baseline on
`main`, commits a `Foo` change on a feature branch, writes the shared index format, and proves
that `FooTest` executes while `BarTest` does not. JUnit Platform consumers must configure
`useJUnitPlatform()` and put the launcher on the test runtime classpath; Gradle 9 does not add
that launcher merely because JUnit Jupiter is declared.

The checked-in Gradle wrapper is 9.6.1. The build compiles to Java 21 bytecode with
`options.release = 21`, so verification can run on the available JDK 25 without silently
raising the consumer bytecode requirement.
