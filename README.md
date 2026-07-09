# Blastradius

A Maven test-impact-selection toolkit for Java/JUnit 5 projects: dynamic, class-level
dependency tracking that lets CI actually skip tests that are safe to skip for a given
change, instead of always running the full suite.

## Modules

- **`blastradius-core`** — the shared engine: the `java.lang.instrument`-based
  dependency-tracking agent and the selection rules (dependency match, conservative
  fallback, always-select-new/modified). Built and tested first, reused unmodified by
  both modules below.
- **`blastradius-maven-plugin`** — the real, installable Maven plugin
  (`blastradius:select`) that gates CI by actually skipping tests during a live build.
  This is the product; see `specs/002-ci-gating-plugin/` for the full spec, plan, and
  task breakdown. **Under active development** — see Status below.
- **`blastradius-validator`** — the original shadow-mode harness used during
  development to validate the dependency-tracking mechanism against real open-source
  projects' commit history before the real plugin was built. Still present and tested,
  useful if you want to re-validate the mechanism against a project of your own; see
  `specs/001-shadow-mode-validator/`.

## How it works

Given a target Java/Maven project, `blastradius-maven-plugin`:

1. **Tracks each test's real dependencies** — a `-javaagent` observes every class
   loaded while a test runs, via bytecode checksums, on a full-suite run against your
   base reference (e.g. `main`).
2. **Diffs the current change** against that base reference (Java source vs.
   everything else — config, resources, `pom.xml`, migrations).
3. **Selects tests**: a test runs if (a) one of its tracked dependencies changed, (b)
   it's new or was itself modified, or (c) a non-source-code change triggered a
   conservative "run everything" fallback.
4. **Hands the selection to Surefire/Failsafe** as a standard test filter — the tests
   that aren't selected simply don't run for this build.

Multi-module Maven reactors are supported: because tracking is based on actual class
loads rather than a static per-module dependency graph, cross-module dependencies are
attributed correctly without any extra bookkeeping.

## Why this is safe to use

The selection mechanism (dependency tracking + conservative fallback rules) is sound
by default, not by absolute guarantee. **We recommend every adopting team also run
their full test suite portfolio on a regular cadence — recommended: daily** — as a
complementary safety net. That combination is the intended trust model: a fast, sound-
by-default selection on every build, backstopped by a full run that catches anything
occasionally missed within a day rather than never. See
`specs/002-ci-gating-plugin/quickstart.md` for the full adoption guide.

## Design principles (from the project constitution, v2.0.0)

- **Test-Driven Development is non-negotiable.** Every piece of engine code was built
  red → green → refactor.
- **Clean code & simplicity.** No speculative abstraction — `blastradius-core` was
  extracted only once a second real consumer needed it.
- **Safety over speed.** Sound, conservative selection is the strong default, but not
  an absolute at any cost — complemented by the recommended daily full-suite run
  above, not a substitute for one.
- **Deterministic before ML.** Selection is pure, explainable dependency tracking —
  no machine learning, no probabilistic shortcuts.
- **Explainability.** Every selection decision carries a concrete reason — dependency
  match, fallback, or new/modified test — never an opaque score.
- **Maintainable, modern foundations.** JUnit 5 Platform, current JDK, no deprecated
  APIs or abandoned tooling.

See `.specify/memory/constitution.md` for the full text and rationale.

## Known limitations

- A class loaded only inside a JUnit 5 `@BeforeAll` (a container-level callback, not a
  test) is never attributed to any specific test, since dependency tracking only
  attributes loads to tests that are actually executing. If such a class later changes
  and breaks a test that depended on it only via `@BeforeAll` setup, that dependency is
  invisible to selection — a narrow, deterministic, and honestly-documented edge case
  (not a bug we're hiding). If this matters for your project, treat `@BeforeAll`-only
  dependencies with extra caution, and lean on the recommended daily full-suite run.
- Refreshing the dependency index (a "track" build) runs the full suite once — correct,
  but not optimized for projects with very slow suites; it only happens on base-
  reference builds, not on every PR build.

## Status

- **`blastradius-core`**: complete, covered by an extensive test suite built through
  strict TDD.
- **`blastradius-validator`**: complete, covered by an extensive test suite (100+
  tests). Its own real-project validation runs (commons-io, jsoup) are documented in
  `SESSION.md`.
- **`blastradius-maven-plugin`**: under active implementation — see
  `specs/002-ci-gating-plugin/tasks.md` for current progress. Not yet ready for real
  adoption; the usage sketch above describes the target design.

See `specs/002-ci-gating-plugin/` for the plugin's full spec, plan, design decisions
(ADR-style research notes), and task breakdown, and `specs/001-shadow-mode-validator/`
for the validator's.
