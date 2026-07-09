<!--
Sync Impact Report
==================
Version change: TEMPLATE → 1.0.0 (initial ratification)
Modified principles: N/A (first fill of template placeholders)
Added sections:
  - I. Test-Driven Development (NON-NEGOTIABLE)
  - II. Clean Code & Simplicity
  - III. Safety Over Speed (NON-NEGOTIABLE)
  - IV. Deterministic Core Before ML
  - V. Shadow-Mode Before Gating (NON-NEGOTIABLE)
  - VI. Explainability
  - VII. Maintainable, Modern Foundations
  - Technology & Architecture Constraints (Section 2)
  - Development Workflow & Quality Gates (Section 3)
  - Governance
Removed sections: none (template placeholders only)
Templates requiring updates:
  - ✅ .specify/templates/plan-template.md — no changes needed; Constitution Check
    gate is derived dynamically from this file, no hardcoded principle names found
  - ✅ .specify/templates/spec-template.md — no hardcoded principle references found
  - ✅ .specify/templates/tasks-template.md — no hardcoded principle references found
  - ✅ .specify/templates/checklist-template.md — no hardcoded principle references found
  - ✅ README.md — placeholder-only, no constitution references to reconcile
  - N/A .specify/templates/commands/ — directory does not exist in this installation
    (this SpecKit install uses .claude/skills/ instead); nothing to reconcile
Follow-up TODOs: none
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
enemies of that trust and of the auditability Principle VI requires.

### III. Safety Over Speed (NON-NEGOTIABLE)

The plugin's entire value proposition rests on trust: a single false negative
(skipping a test that would have failed) is a strictly worse outcome than running
the entire suite unnecessarily. Every design decision, when faced with ambiguity or
incomplete information, MUST default to running more tests, never fewer. Changes
that fall outside what the dependency-tracking mechanism can soundly observe —
resource files, `pom.xml` and dependency version changes, database migrations,
build configuration — MUST trigger a conservative fallback that runs the full
affected scope rather than attempting a narrower guess. Soundness is non-negotiable;
speed is the secondary objective and MUST NOT be traded against it.

**Rationale**: This tool exists to be trusted enough that teams let it gate merges.
One confirmed false negative in production is sufficient to end that trust
permanently, as the history of comparable tools (Principle VII) demonstrates.

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

### V. Shadow-Mode Before Gating (NON-NEGOTIABLE)

No selection logic — the deterministic core or any future ML layer — may be trusted
to actually skip tests in a real CI pipeline until it has first run in shadow mode:
selecting a subset while still executing the full suite, and reporting what would
have been skipped versus what would have actually failed. A capability MUST NOT be
promoted from shadow mode to gating mode until it has demonstrated a would-miss rate
of zero over a meaningful, representative sample of real changes. This gate applies
independently to each capability — the deterministic engine earns trust first; any
later ML layer MUST re-earn it separately, even after the deterministic core is
already trusted and gating.

**Rationale**: Trust must be demonstrated with evidence on real usage, not assumed
from design soundness alone. Shadow mode is the mechanism that lets adopters verify
safety for themselves before any risk is taken with their CI pipeline.

### VI. Explainability

Every selection decision MUST be traceable to a concrete, human-readable reason: which
changed class or file a given test's tracked dependencies intersect with, or which
conservative fallback rule triggered a broader run. Opaque or unexplainable skip
decisions (e.g., a bare confidence score with no supporting reason) MUST NOT be
surfaced to users as the basis for skipping a test without also surfacing the
reasoning behind it.

**Rationale**: Explainability is what separates a trustworthy skip from a black box.
It is also the mechanism by which users audit and challenge the tool's decisions,
which is essential to earning the trust Principle V requires evidence for.

### VII. Maintainable, Modern Foundations

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
- Any feature that changes what gets skipped, or introduces a new selection or
  fallback mechanism, MUST include a shadow-mode validation plan (Principle V) as
  part of its spec before it may be considered for gating behavior.
- Code review MUST verify: tests were written first where feasible to confirm
  (Principle I), no speculative abstractions were introduced (Principle II), no
  design decision silently weakens soundness in favor of speed (Principle III), and
  any new dependency introduced is on an actively maintained foundation
  (Principle VII).

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

**Version**: 1.0.0 | **Ratified**: 2026-07-08 | **Last Amended**: 2026-07-08
