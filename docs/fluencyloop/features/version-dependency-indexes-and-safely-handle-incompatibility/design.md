# Design: Version dependency indexes and safely handle incompatibility (#29)

started: 2026-07-21

## Class diagram

```mermaid
classDiagram
  class DependencyIndexFormat {
    +CURRENT_VERSION int
    +migrateLegacy(version) int
    +isCurrent(version) boolean
  }
  class MavenDependencyIndex {
    +formatVersion int
  }
  class GradleDependencyIndex {
    +formatVersion int
  }
  class FileIndexStore {
    +get(key) Optional
    +put(key, value)
  }
  class MavenIndexApplicabilityResolver {
    +resolve() IndexApplicability
  }
  class GradleIndexApplicabilityResolver {
    +resolve() IndexApplicability
  }

  DependencyIndexFormat <.. MavenDependencyIndex : normalizes legacy version
  DependencyIndexFormat <.. GradleDependencyIndex : normalizes legacy version
  FileIndexStore --> MavenDependencyIndex : reads and writes JSON
  FileIndexStore --> GradleDependencyIndex : reads and writes JSON
  MavenIndexApplicabilityResolver --> MavenDependencyIndex
  GradleIndexApplicabilityResolver --> GradleDependencyIndex
```

## Sequence: read an index safely

```mermaid
sequenceDiagram
  participant Store as FileIndexStore
  participant Model as DependencyIndex
  participant Resolver as ApplicabilityResolver
  participant Build as Maven or Gradle test task

  Store->>Model: deserialize JSON
  alt unversioned legacy JSON
    Model->>Model: migrate version 0 to version 1 in memory
  end
  Model->>Resolver: index with formatVersion
  alt current format version
    Resolver->>Build: SELECT with the index
  else unsupported format version
    Resolver->>Build: named safe FALLBACK
  end
```

## Compatibility cases

| Stored index | Handling |
| --- | --- |
| No `formatVersion` field (the existing schema) | Migrate in memory as known legacy version 0. |
| `formatVersion: 1` | Read and use as the current schema. |
| Any other version | Report a named format mismatch and leave the test task unfiltered. |

## Decision

Keep the format contract in `blastradius-core` because Maven and Gradle persist the same index
schema. Migrate only the known missing-version legacy schema in memory; reject any other version
through each adapter's existing safe-fallback path. A hard failure would needlessly block a build,
while accepting an unfamiliar schema could make an unsound selection.
