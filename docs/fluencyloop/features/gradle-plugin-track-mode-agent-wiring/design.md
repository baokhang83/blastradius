# Design: Gradle plugin TRACK mode agent wiring

started: 2026-07-20

## Class diagram

```mermaid
classDiagram
  class BlastradiusPlugin {
    +apply(Project)
  }
  class GradleTrackAction {
    +prepare(Test) boolean
    +complete()
  }
  class GradleSelectAction {
    +apply(Test)
  }
  class CurrentChangesResolver {
    +resolve(Path, String) CurrentChanges
  }
  class AgentJarLocator {
    +locate() Path
  }
  class DependencyTrackingAgent {
    +premain(String, Instrumentation)
  }
  class DependencyRecordReader {
    +readAll(Path) Map
  }
  class DependencyIndexWriter {
    +write(Path, DependencyIndex)
  }
  class Test
  class agentJar {
    <<Gradle artifact>>
  }

  BlastradiusPlugin --> GradleTrackAction : prepares / completes
  BlastradiusPlugin --> GradleSelectAction : selects non-baseline builds
  GradleTrackAction --> CurrentChangesResolver : identifies base build
  GradleTrackAction --> AgentJarLocator : resolves self-contained agent
  GradleTrackAction --> Test : adds -javaagent before execution
  GradleTrackAction --> DependencyRecordReader : merges worker records
  GradleTrackAction --> DependencyIndexWriter : persists shared index
  AgentJarLocator --> agentJar
  agentJar --> DependencyTrackingAgent : Premain-Class
```

## Sequence: base-reference TRACK build

```mermaid
sequenceDiagram
  participant G as Gradle
  participant P as BlastradiusPlugin
  participant T as Test task
  participant A as Tracking agent
  participant R as DependencyRecordReader
  participant I as .blastradius/index.json

  G->>P: apply plugin
  P->>T: install doFirst / doLast actions
  G->>T: execute test on baseRef
  T->>P: doFirst
  P->>P: resolve HEAD == baseRef
  P->>T: add -javaagent:agent.jar=record-prefix
  T->>A: start test worker JVM
  A->>A: record classes per active JUnit test
  A-->>R: write record-prefix.pid at JVM shutdown
  T->>P: doLast
  P->>R: merge all worker records
  R-->>P: test dependencies
  P->>I: write anchor commit + dependencies
  Note over I: A later Gradle or Maven SELECT build consumes this format
```

## Boundaries and decisions

- The existing `blastradius-core` Gradle project will expose a self-contained `agentJar` with
  the agent manifest and runtime dependencies. The Gradle plugin depends on that artifact, so
  `DependencyTrackingAgent` resolves to a real, attachable JAR both in a published plugin and
  in Gradle TestKit. A thin core JAR is not sufficient: `-javaagent` does not automatically
  bring Jackson, which the shutdown hook needs to write records.
- `GradleTrackAction` runs in the `Test` task's `doFirst`/`doLast` boundary. `doFirst` is early
  enough to add JVM arguments before Gradle forks the worker; `doLast` is late enough for every
  worker shutdown hook to have written its unique record. No nested Gradle build is needed.
- A base-reference build is TRACK; a non-base build keeps the existing SELECT/fallback path.
  TRACK never filters tests. Failure to read tracking output fails the TRACK build rather than
  publishing a partial index.
- This slice deliberately does not model tracking as cacheable Gradle task inputs or force
  up-to-date `Test` tasks to rerun; issue #19 owns that compatibility work. The functional
  proof starts from a clean consumer build, where Gradle executes the test task normally.
