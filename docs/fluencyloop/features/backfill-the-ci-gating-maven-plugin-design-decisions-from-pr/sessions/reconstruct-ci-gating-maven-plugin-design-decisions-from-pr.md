# Session: Reconstruct CI-gating Maven plugin design decisions from PR #1

- **intent:** Reconstruct CI-gating Maven plugin design decisions from PR #1
- **started:** 2026-07-20

> **Backfilled from contemporaneous records.** Sources are the PR #1 implementation diff
> (`f0d1ba6` -> `c3cf16e`), the `specs/002-ci-gating-plugin/` package, and `SESSION.md` at
> `c3cf16e`. All four reconstructed decisions were maintainer-confirmed on 2026-07-20.

## Decision: Route one select goal among TRACK, SELECT, and FALLBACK

- **where:** `blastradius-maven-plugin/.../mojo/SelectMojo.java; specs/002-ci-gating-plugin/research.md §1`
- **why:** A base-reference build refreshes the shared index, a non-base build with an applicable index narrows Surefire, and a cold non-base build safely runs the full suite without paying for a transient index.
- **alternative:** Fork tracking whenever an index is missing, always track and select, or require a separately wired blastradius:track goal — rejected in the contemporaneous research because PR builds would pay a redundant full run, every build would lose the intended savings, or adopters would need two CI paths.
- **design:** [Mode routing](../design.md#sequence-one-blastradiusselect-invocation) — `SelectMojo`, `CurrentChangesResolver`, `IndexApplicabilityResolver`, `SurefireFilterApplier`, `BuildReport`
- **constitution:** §II, §III, §IV
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-20)

## Decision: Attach the tracking agent only to a child Maven subprocess

- **where:** `blastradius-maven-plugin/.../track/TrackRunner.java; SelectMojo TRACK path`
- **why:** Tracking runs in a separately invoked mvn clean test with the agent supplied through JAVA_TOOL_OPTIONS, leaving the ambient gated build and its Surefire configuration unchanged.
- **alternative:** Set argLine from the live Mojo — rejected because a hard-coded argLine or common JaCoCo setup can override it, silently preventing agent attachment.
- **design:** [Tracking boundary](../design.md#sequence-one-blastradiusselect-invocation) — `SelectMojo`, `TrackRunner`, `TrackChild`, `AmbientBuild`
- **constitution:** §II, §III, §VI
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-20)

## Decision: Persist the dependency index locally outside target

- **where:** `SelectMojo indexPath default; plugin/index/DependencyIndexReader.java and DependencyIndexWriter.java; contracts/mojo-and-index-contract.md`
- **why:** A local .blastradius/index.json keeps the plugin infrastructure-free and allows an adopting team's normal CI cache to carry the index between runners without losing it to mvn clean.
- **alternative:** Use a hosted index service or store it under target — rejected because a service adds infrastructure outside the feature's scope, while target is conventionally deleted by clean.
- **design:** [Index storage](../design.md#class-diagram) — `DependencyIndex`, `IndexApplicabilityResolver`, `.blastradius/index.json`
- **constitution:** §II, §IV
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-20)

## Decision: Treat a reachable recorded anchor as an applicable index

- **where:** `plugin/index/DependencyIndex.java; IndexApplicabilityResolver.java; specs/002-ci-gating-plugin/research.md §3`
- **why:** The index records a concrete base commit rather than a moving branch name, so it is intended to remain usable while its anchor is still present in history; missing, unreadable, or unreachable anchors fall back to the full suite.
- **alternative:** Expire the index after a fixed time window — rejected because age does not determine whether a commit anchor is valid or whether a diff can be computed.
- **design:** [Anchor semantics](../design.md#review-point-index-anchor-semantics) — `DependencyIndex.anchorCommit`, `IndexApplicabilityResolver`, `CurrentChangesResolver`
- **constitution:** §III, §IV
- **note:** The maintainer confirmed the intended anchor rule. `research.md` describes diffing forward from the recorded anchor when `baseRef` advances, while the shipped resolver computes changes from the current `baseRef`; determining whether a runtime change is needed is separate follow-up work.
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-20)

## Knowledge transfer

### Invocation and routing

- **`SelectMojo` / mode routing:** the single `blastradius:select` goal resolves the reactor-root
  repository, current commit, base ref, and index state before Surefire. Base-ref builds (or an
  explicit track override) take `TRACK`; non-base builds with an applicable index take `SELECT`;
  all other non-base builds take `FALLBACK`. **Status:** documented.
- **`CurrentChangesResolver`:** resolves both refs to concrete commits, recognises a base-ref
  build, and otherwise classifies the changed files for the shared selection engine. It is the
  bridge between Git state and a live Maven invocation. **Status:** documented.
- **`BuildReport` plus console/explain renderers:** all three modes emit a machine-readable
  report and human-readable summary. Only `SELECT` contains per-test decisions; `TRACK` and
  `FALLBACK` mean the ambient suite remains whole. **Status:** documented.

### Tracking and persistence

- **`TrackRunner`:** starts a distinct `mvn clean test` child with the tracking agent appended
  to `JAVA_TOOL_OPTIONS`, waits for it, merges recorded dependencies, and builds an index anchored
  to the tracked commit. The child guard prevents the same Mojo in that child from recursively
  tracking again. **Status:** documented.
- **`DependencyIndex` and its reader/writer:** persist test-to-class dependencies and a concrete
  commit SHA in `.blastradius/index.json`; the index belongs to the project working directory,
  so CI must restore/save it through the team's cache rather than source control. **Status:** documented.
- **`IndexApplicabilityResolver`:** rejects missing, unreadable, and unreachable-anchor indexes
  before selection, preserving an unfiltered full-suite fallback instead of guessing. **Status:**
  documented; see the anchor-semantics review point below.

### Selection and safeguards

- **`SelectionEngine` and `SurefireFilterApplier`:** selection reuses the core engine's tracked
  dependencies and conservative rules, then supplies the selected identities to Maven's normal
  test filter. The plugin does not attach an agent or otherwise alter JVM startup for the live
  gated build. **Status:** documented.
- **Reactor-root resolution:** the PR corrected module-relative repository/index lookup by using
  Maven's execution root. That keeps a multi-module build on one Git repository and one shared
  index rather than silently creating empty module-local views. **Status:** documented.
- **Anchor semantics:** contemporaneous research intends a reachable old anchor to remain usable
  with a forward diff, but the shipped diff resolver uses the configured base ref. Confirm whether
  this is an intended equivalent, a later change, or a gap; the documented decision itself is
  maintainer-confirmed, but no runtime change is included here.
  **Status:** follow-up.
