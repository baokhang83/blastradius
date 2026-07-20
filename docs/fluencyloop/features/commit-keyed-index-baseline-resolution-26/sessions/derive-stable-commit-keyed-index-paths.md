# Session: Derive stable commit-keyed index paths

- **intent:** Derive stable commit-keyed index paths
- **started:** 2026-07-20

## Decision: Derive one canonical key from the full resolved commit

- **where:** `blastradius-core/.../index/CommitIndexKey.java`
- **why:** Maven, Gradle, and a later remote store need the same deterministic object name; inserting the full SHA before the configured file name preserves the configured namespace.
- **alternative:** Append a SHA suffix or scan stored indexes - rejected because suffix semantics vary and scanning can choose a stale baseline.
- **design:** ../design.md#class-diagram
- **constitution:** §II, §IV
- **trust:** ✓ verified

## Knowledge transfer

- **CommitIndexKey:** turns a root-relative configured index-file key and a full resolved Git
  SHA into one slash-separated storage key. The commit is an interior path segment, so the
  default writes `.blastradius/<sha>/index.json` and custom parents or filenames stay intact.
  **Status:** documented.
- **Input validation:** only complete 40-hex-character object IDs and root-relative path keys
  are accepted. This prevents an abbreviated ref or unsafe path from producing an ambiguous
  cache location before a store performs I/O. **Status:** verified by `CommitIndexKeyTest`.
