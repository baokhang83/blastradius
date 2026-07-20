# Design: Gradle plugin scaffold and SELECT wiring

started: 2026-07-20

## Adapter shape

```mermaid
classDiagram
  class BlastradiusGradlePlugin {
    +apply(Project)
  }
  class BlastradiusExtension {
    +baseRef
    +indexPath
  }
  class TestTask {
    +filter.includeTest(class, method)
  }
  class GradleSelectAction {
    +select(Test)
  }
  class DependencyIndexReader {
    +read(Path)
  }
  class CurrentChangesResolver {
    +resolve(Path, baseRef)
  }
  class TestDiscoverer {
    +discover(Test)
  }
  class SelectionEngine {
    <<blastradius-core>>
    +selectAll(...)
  }

  BlastradiusGradlePlugin --> BlastradiusExtension : creates
  BlastradiusGradlePlugin --> TestTask : configures each
  TestTask --> GradleSelectAction : runs before tests
  GradleSelectAction --> DependencyIndexReader : reads shared index
  GradleSelectAction --> CurrentChangesResolver : computes diff
  GradleSelectAction --> TestDiscoverer : finds current tests
  GradleSelectAction --> SelectionEngine : computes decisions
  GradleSelectAction --> TestTask : adds selected filters
```

## SELECT flow

```mermaid
sequenceDiagram
  participant User as Gradle consumer
  participant Plugin as Blastradius plugin
  participant Test as Gradle Test task
  participant Git as Git and index
  participant Core as SelectionEngine
  participant JUnit as JUnit Platform

  User->>Plugin: apply io.github.baokhang83.blastradius
  Plugin->>Test: register pre-test selection action
  User->>Test: gradle test
  Test->>Git: resolve baseRef and index
  alt usable index on non-base build
    Test->>JUnit: discover compiled tests
    Test->>Core: selectAll(tests, dependencies, changed files)
    Core-->>Test: selected TestIdentity values
    Test->>Test: includeTest for each selection
  else base build or unusable index
    Test->>Test: leave task unfiltered
  end
  Test->>JUnit: execute filtered or full suite
```

## Key decisions

- The new `blastradius-gradle-plugin` is a Gradle `java-gradle-plugin` adapter. It consumes
  `blastradius-core` directly and keeps selection rules in the existing core rather than copying
  them into Gradle code.
- The extension exposes the same `baseRef` and `indexPath` concepts as Maven. SELECT is applied
  through Gradle's `Test.filter.includeTest`, the programmatic equivalent of `--tests`.
- This issue implements only SELECT. A base-reference build or an unusable index leaves Gradle's
  test task unfiltered; TRACK agent wiring belongs to issue #20, and configuration-cache work to
  issue #19.
- Functional tests use Gradle TestKit to run a real temporary Gradle project. A configuration-only
  test would not prove that the `Test` task actually receives and honours the selected filters.
