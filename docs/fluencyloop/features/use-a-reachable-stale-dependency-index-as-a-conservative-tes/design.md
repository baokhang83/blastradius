# Design: Use a reachable stale dependency index as a conservative test-selection baseline

started: 2026-07-26

## Class diagram

```mermaid
classDiagram
  class IndexStore~T~ {
    +get(key) Optional~T~
    +put(key, value)
    +keys(prefix) List~String~
  }
  class FileIndexStore~T~
  class S3IndexStore~T~
  class BaselineIndexResolver {
    +resolve(store, exactKey, prefix, expectedBase, head) BaselineResolution
  }
  class BaselineResolution {
    +index DependencyIndex
    +anchorCommit String
    +stale boolean
  }
  class CurrentChangesResolver {
    +resolveFromAnchor(project, changes, anchor) CurrentChanges
  }
  class SelectMojo

  IndexStore <|.. FileIndexStore
  IndexStore <|.. S3IndexStore
  BaselineIndexResolver --> IndexStore
  SelectMojo --> BaselineIndexResolver
  SelectMojo --> CurrentChangesResolver
  BaselineResolution --> DependencyIndex
```

## Sequence: select with a stale baseline

```mermaid
sequenceDiagram
  participant Mojo as SelectMojo
  participant Resolver as BaselineIndexResolver
  participant Store as IndexStore
  participant Git as CurrentChangesResolver
  participant Engine as SelectionEngine

  Mojo->>Resolver: resolve exact base and ancestor candidates
  Resolver->>Store: get exact commit key
  alt Exact baseline exists
    Store-->>Resolver: exact index
    Resolver-->>Mojo: exact baseline
  else Exact baseline missing
    Resolver->>Store: keys below index prefix
    Resolver->>Resolver: keep valid ancestors of HEAD and choose nearest
    Resolver-->>Mojo: stale baseline with anchor
    Mojo->>Git: diff anchor to HEAD
    Git-->>Mojo: widened changed files
  end
  Mojo->>Engine: select using baseline index and changed files
  Engine-->>Mojo: conservative selected tests
```

## Design decision

The exact base-commit index remains preferred. If it is unavailable, the plugin may use
the nearest valid ancestor index only when the anchor is reachable from the tested HEAD.
It then computes changes from that anchor through HEAD, which includes intervening main
changes and the PR change. An unrelated or descendant index is never used; if no ancestor
baseline is available, the build still runs the full suite.

The index-store interface grows a prefix enumeration operation because both supported
backends, filesystem and S3, need to expose the same candidate search. This is a concrete
shared need, rather than a speculative abstraction.
