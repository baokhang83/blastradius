<!--
Sync Impact Report
==================
Version change: 2.0.0 → 2.1.0 (MINOR — Principle III materially refined)
Modified principles:
  - III. Safety Over Speed — added the inert-change carve-out: a change that provably
    cannot affect any test outcome (documentation, license text, images, and other
    non-executable files never loaded as classes) MUST NOT trigger the conservative
    full-scope fallback; the sound selection for it is zero tests. The carve-out stays
    deterministic and explainable (Principles IV, V) — inert files are recognized by a
    fixed path classification, not a probabilistic guess — so soundness is not weakened:
    an inert change has no test that could catch it.
    Rationale: the current binary classifier (ChangedFileClassifier: JAVA_SOURCE vs
    NON_SOURCE) routes provably-inert changes such as a README edit to a full-suite run,
    which is wasteful, not safer.
Added principles: none
Removed principles: none
Added/removed sections: none
Templates requiring updates:
  - ✅ .specify/templates/plan-template.md — Constitution Check derived dynamically from
    this file, no changes needed
Follow-up TODOs:
  - ⚠ Implementation: ChangedFileClassifier needs a third INERT classification (a
    deterministic inert-path allowlist that selects zero tests) to honor this principle.
    Tracked as a follow-up feature, not part of this amendment.
-->

# Blastradius Constitution

## Core Principles

### I. Test-Driven Development (NON-NEGOTIABLE)

All engine code MUST be written red-green-refactor: a failing test is written first,
the minimum code to pass it is written second, and cleanup happens third with tests
kept green throughout. No production code is written without a prior failing test
that justifies it. Every unit of selection logic — dependency tracking, checksum
comparison, fallback triggers, index persistence — MUST have direct, fast, isolated
tests before it is wired into the plugin.

**Rationale**: This is how every prior component in this family of tools has been
built, and it produces the trustworthy, well-specified behavior that a test-selection
tool's own credibility depends on. A tool that decides which tests to skip cannot
itself be undertested.

### II. Clean Code & Simplicity

Code MUST favor small, single-purpose classes and functions over large, multi-purpose
ones. Abstractions MUST NOT be introduced speculatively — build the concrete case
first; extract an abstraction only once a second real use demonstrates the shared
shape. Comments MUST be reserved for non-obvious *why* (a hidden constraint, a subtle
invariant, a workaround); code MUST NOT carry comments that restate *what* well-named
identifiers already show. Duplication of a few lines is preferred over a premature
shared abstraction that guesses at future requirements.

**Rationale**: A test-selection engine's correctness depends on the reader being able
to trust what the code visibly does. Cleverness and speculative generality are the
enemies of that trust and of the auditability Principle V requires.

### III. Safety Over Speed

The plugin favors sound, conservative selection by default — dependency-tracking-
driven decisions, not speculative heuristics — because an unnecessary full run is a
much smaller cost than a missed failure. Ambiguity in what a change touches SHOULD
default to running more tests rather than fewer. Changes that fall outside what the
dependency-tracking mechanism can soundly observe — resource files, `pom.xml` and
dependency version changes, database migrations, build configuration — MUST still
trigger a conservative fallback that runs the full affected scope, since that
mechanism is cheap to keep and doesn't trade away meaningful speed. This fallback
applies to unobservable changes that *could* affect a test outcome; a change that
provably *cannot* affect any test outcome — documentation, license text, images, and
other non-executable files that are never loaded as classes — MUST NOT trigger a full
run, since the sound selection for it is zero tests. This carve-out stays deterministic
and explainable (Principles IV and V): inert files are recognized by a fixed path
classification, not a probabilistic guess, and the surfaced reason is simply that the
change matched no executable scope.

This is a strong default, not an absolute, non-negotiable rule: adopting teams are
expected to run their full test suite portfolio on a regular cadence (recommended:
daily) as a complementary safety net. That complementary practice is what allows the
plugin's own selection logic to prioritize practical speed rather than treating every
edge case with maximal, at-all-costs conservatism.

**Rationale**: The original, stricter framing of this principle assumed the plugin's
own selection was the only safety net a team had, which made any false negative
unacceptable at any cost. With a complementary full-suite-portfolio cadence
recommended alongside it, an occasional missed failure is caught within a day rather
than never — changing the cost-benefit calculus enough that soundness no longer needs
to be treated as non-negotiable in every edge case to remain trustworthy.

### IV. Deterministic Core Before ML

The core dependency-selection engine MUST be deterministic: dynamic, class-level
bytecode dependency tracking with checksums, computed the same way given the same
inputs, requiring zero historical data and functioning correctly on a project's very
first run. Machine learning capabilities (test prioritization, predictive trimming,
flaky-test detection) MAY be added only as optional, additive, later-phase layers.
An ML layer MUST NOT be a dependency of the deterministic core, MUST NOT be enabled
by default, and MUST NOT introduce probabilistic test-skipping without being
explicitly opted into by the user, independent of the core engine's operation.

**Rationale**: ML-based selection requires a training corpus the tool cannot have on
day one, and it trades an explainable, sound guarantee for a statistical one. The
deterministic core must stand on its own merits — explainable and immediately useful
— before any probabilistic layer is allowed to touch what gets skipped.

### V. Explainability

Every selection decision MUST be traceable to a concrete, human-readable reason: which
changed class or file a given test's tracked dependencies intersect with, or which
conservative fallback rule triggered a broader run. Opaque or unexplainable skip
decisions (e.g., a bare confidence score with no supporting reason) MUST NOT be
surfaced to users as the basis for skipping a test without also surfacing the
reasoning behind it.

**Rationale**: Explainability is what separates a trustworthy skip from a black box.
It is also the mechanism by which users audit and challenge the tool's decisions,
which is essential to earning and keeping the trust this whole project depends on.

### VI. Maintainable, Modern Foundations

The project MUST be built on actively maintained, current foundations: the JUnit 5
Platform as the native test execution model (with JUnit 4/TestNG support treated as
compatibility, not the primary target), current JDK versions, and standard Java agent
instrumentation for dependency tracking. The project MUST NOT depend on deprecated or
removed APIs (e.g., the Java Security Manager) or on abandoned third-party tooling as
a foundation.

**Rationale**: Prior art in this space (Ekstazi, STARTS, Arquillian Smart Testing)
demonstrates the failure mode directly: tools built on JUnit 4-only assumptions and
now-removed JDK mechanisms became unusable as the ecosystem moved on. Building on
current, actively maintained foundations from the start is a precondition for the
project outliving its predecessors.

## Technology & Architecture Constraints

- **Test execution model**: JUnit 5 Platform (Jupiter) is the primary, native
  integration target. Maven Surefire (unit tests) and Failsafe (integration tests)
  MUST both be supported, since integration tests are where selection saves the most
  time.
- **Dependency tracking mechanism**: dynamic, class-level tracking via standard Java
  agent instrumentation, recording each test's actually-exercised class dependencies
  and their bytecode checksums. Static-analysis-only tracking MUST NOT be the sole
  mechanism, as it cannot observe reflective, dependency-injection, or classpath-
  scanning edges that Java applications commonly rely on.
- **Selection input**: a persistent, cacheable dependency index anchored on a base
  ref (e.g. `main`), combined with a git diff for the changed ref, MUST be the
  supported mechanism for PR/CI selection — not an ad hoc per-run full re-analysis.
- **JDK/runtime**: current JDK LTS versions MUST be supported without requiring
  deprecated or removed APIs (no Security Manager dependency, no deprecated attach
  mechanisms).

## Development Workflow & Quality Gates

- Every feature begins with a failing test (Principle I) and a written spec
  (`/speckit-specify`) before implementation planning.
- Every `/speckit-plan` MUST pass the Constitution Check gate against this document
  before Phase 0 research begins, and MUST be re-checked after Phase 1 design.
- Code review MUST verify: tests were written first where feasible to confirm
  (Principle I), no speculative abstractions were introduced (Principle II), no
  design decision silently weakens soundness in favor of speed (Principle III), and
  any new dependency introduced is on an actively maintained foundation
  (Principle VI).

## Governance

This constitution supersedes all other project practices and conventions. Any
conflict between this document and other guidance (README, code comments, prior
habit) is resolved in favor of this document.

**Amendment procedure**: amendments are proposed as a change to this file, must
state the rationale for the change, and must include the Sync Impact Report required
by the SpecKit constitution workflow. Amendments take effect on merge to the main
branch.

**Versioning policy**: this constitution is versioned independently using semantic
versioning:
- **MAJOR**: backward-incompatible governance changes, or removal/redefinition of an
  existing principle.
- **MINOR**: a new principle or materially expanded section is added.
- **PATCH**: clarifications, wording fixes, and other non-semantic refinements.

**Compliance review**: every implementation plan MUST pass the Constitution Check
gate defined in `.specify/templates/plan-template.md`. Any violation MUST be
explicitly justified in that plan's Complexity Tracking section or the design MUST
be simplified until it complies.

**Version**: 2.1.0 | **Ratified**: 2026-07-08 | **Last Amended**: 2026-07-11
