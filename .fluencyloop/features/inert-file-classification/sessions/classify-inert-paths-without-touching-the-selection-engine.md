# Session: classify inert paths without touching the selection engine

- **intent:** classify inert paths without touching the selection engine
- **started:** 2026-07-11

---

## Decision: model INERT as a third FileKind, changing no selection code

- **where:** `blastradius-core/.../git/FileKind.java`, `core/selection/SelectionEngine.java`
- **why:** the fallback rule triggers only on NON_SOURCE and dependency-matching draws class
  names only from JAVA_SOURCE. An INERT file is neither, so the existing engine already yields
  "no fallback, no match" — zero tests — for an inert-only change, with no new branch. Inert is
  safe *by construction*, not by an added rule.
- **alternative:** add an explicit "select nothing for inert" branch in
  SelectionEngine/FallbackSelector — rejected: more code, another branch to keep sound, and it
  makes it *possible* to accidentally fall back on an inert change; the by-construction approach
  makes that impossible.
- **design:** ../design.md#sequence-a-readme-only-change-selects-zero-tests
- **constitution:** §III (sound by default) — inert selects zero without weakening the fallback
- **trust:** ✓ verified — 45/45 core tests green; existing selection tests pass unchanged, and
  `SelectionEngineTest.inertOnlyChangeSelectsNoTestsAndDoesNotFallback` locks the promise.

## Decision: allowlist that excludes anything under resources/

- **where:** `blastradius-core/.../git/ChangedFileClassifier.isInert`
- **why:** a Markdown/YAML/image under a `resources/` directory can be test data a test loads at
  runtime; class-load tracking only records *class* loads, so that change is invisible to it —
  exactly why NON_SOURCE→fallback exists. Classifying it INERT would skip tests that depend on
  it. So `isInert` returns false for any `resources/` path, and inert is an allowlist (unmatched
  → NON_SOURCE), never a blocklist.
- **alternative:** blanket extension match (e.g. any `*.md` → INERT) — rejected: unsound; a
  `.md` fixture under `src/test/resources` would wrongly select nothing.
- **constitution:** §III (a false "inert" call skips tests that should run) and §IV
  (deterministic path rule, not a probabilistic guess)
- **trust:** ✓ verified — guarded by `ChangedFileClassifierTest.markdownUnderResourcesIsNonSourceNotInert`.
