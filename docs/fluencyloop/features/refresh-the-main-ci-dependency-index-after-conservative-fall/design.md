# Design: Refresh the main CI dependency index after conservative fallback and prevent stale index promotion

started: 2026-07-26

## Class diagram

```mermaid
classDiagram
  class RestoreBlastradiusIndex {
    cache-hit: exact key only
  }
  class VerifyBuild {
    modeArgument()
  }
  class SelectMojo {
    determineMode()
  }
  class TrackRunner {
    track(currentCommit)
  }
  class FileIndexStore {
    put(commitKey, index)
  }
  class SaveBlastradiusIndex {
    cache key = current commit SHA
  }

  RestoreBlastradiusIndex --> VerifyBuild : exact miss selects TRACK
  VerifyBuild --> SelectMojo : -Dblastradius.mode=track
  SelectMojo --> TrackRunner : fresh dependency data
  TrackRunner --> FileIndexStore : writes current commit index
  FileIndexStore --> SaveBlastradiusIndex : archives fresh baseline
```

## Sequence: refresh a main baseline after an exact-cache miss

```mermaid
sequenceDiagram
  participant Cache as GitHub Actions cache
  participant Verify as Maven verify step
  participant Plugin as SelectMojo
  participant Tracker as TrackRunner
  participant Store as FileIndexStore

  Cache-->>Verify: restore exact key or older prefix match
  alt main build with no exact cache hit
    Verify->>Plugin: -Dblastradius.mode=track
    Plugin->>Tracker: track checked out commit
    Tracker-->>Plugin: complete dependency index
    Plugin->>Store: write index under checked out SHA
    Verify->>Cache: save archive under that same SHA
  else exact cache hit
    Verify->>Plugin: normal SELECT invocation
    Note over Plugin: reuse the current-commit index
  else pull request build
    Verify->>Plugin: normal SELECT invocation
    Note over Plugin: a reachable baseline may be used conservatively
  end
```

## Design rationale

An Actions cache is an archive, not proof of which commit produced its contents. A prefix
restore is allowed to supply a usable older baseline for a pull request, but a main build that
does not restore its exact commit key must refresh that baseline before it saves an archive under
the current SHA. The workflow therefore explicitly requests the plugin's existing `TRACK` mode
only for a main build with an exact-cache miss. `TRACK` records dependencies for the checked-out
commit and writes an index keyed by that commit. A rerun with an exact cache hit keeps the normal
selection path and avoids unnecessary tracking.

This keeps the index payload and cache key aligned. It avoids a new engine abstraction and relies
on the existing explicit mode switch, `TrackRunner`, and commit-keyed `FileIndexStore`.
