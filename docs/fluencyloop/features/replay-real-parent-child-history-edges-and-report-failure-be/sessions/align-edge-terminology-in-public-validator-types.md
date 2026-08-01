# Session: align edge terminology in public validator types

- **intent:** align edge terminology in public validator types
- **started:** 2026-08-01

## Knowledge transfer

- **Public validator terminology:** `commitWindowSize` and `CommitPair` now describe direct
  parent-child history edges. The `--commits` flag is retained for compatibility, but its count
  is explicitly an edge budget in the current replay model. · status: documented
