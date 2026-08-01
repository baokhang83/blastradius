# Session: model reachable history as direct parent-child edges

- **intent:** model reachable history as direct parent-child edges
- **started:** 2026-08-01

## Knowledge transfer

- **CommitWindowResolver:** walks the commits reachable from `HEAD`, but now turns each visited
  child into one or more actual parent-to-child `CommitPair` values. The edge budget is consumed
  by emitted edges, so a merge can occupy more than one slot. This prevents a `RevWalk` ordering
  detail from being mistaken for a repository transition. · status: documented
- **HistoryMode:** makes the graph-coverage policy explicit. `ALL_PARENTS` preserves reachable
  merged-branch evidence and is the default; `FIRST_PARENT` remains available when an operator
  intentionally wants mainline-only replay. · status: documented

## Decision: replay direct parent edges with all parents by default

- **where:** `CommitWindowResolver and HistoryMode`
- **why:** each reported pair must be a real Git transition while retained merged-branch commits still contribute evidence
- **alternative:** Pair adjacent RevWalk entries or make first-parent the only mode — rejected: adjacency can fabricate a transition and first-parent-only discards reachable branch evidence
- **design:** ../design.md#direct-graph-edges-not-traversal-adjacency
- **constitution:** §III
- **trust:** ✓ verified
