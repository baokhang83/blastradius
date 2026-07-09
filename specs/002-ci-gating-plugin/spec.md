# Feature Specification: CI-Gating Maven Plugin

**Feature Branch**: N/A — no git branch created for this spec (git extension not
installed in this SpecKit setup); tracked via spec directory
`specs/002-ci-gating-plugin`

**Created**: 2026-07-09

**Status**: Draft

**Input**: User description: "A real Maven plugin (working name:
blastradius-maven-plugin) that gates CI by actually skipping tests during a live
`mvn test` run, using the same dynamic class-level dependency-tracking engine already
proven safe by the shadow-mode validator (T061: PASS, zero would-miss cases across
105 real commit pairs on commons-io and jsoup). Integration model: a custom Maven
goal that computes the test selection for the current changes and passes it through
to Surefire/Failsafe as a test filter — opt-in, sits alongside the normal `mvn test`,
adoptable incrementally per-project."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Get a faster build on an ordinary code change (Priority: P1)

As a developer on a Java/Maven project, I make a small, typical source change, run my
build with the plugin enabled, and only the tests actually affected by my change run
— saving real wall-clock time — while the build's pass/fail outcome is exactly what
the full, unmodified suite would have reported.

**Why this priority**: This is the entire reason the plugin exists — the underlying
selection mechanism was already built and tested (see `blastradius-core`); this is
where it finally translates into a faster real build, which is the whole business
case.

**Independent Test**: Can be fully tested by making a small, contained source change
in a project with the plugin enabled, running the build, and confirming: (a) fewer
tests executed than the full suite would have, (b) the build's overall pass/fail
result matches what a full, unmodified run of the same change would have produced.

**Acceptance Scenarios**:

1. **Given** a project with the plugin enabled and a prior baseline established,
   **When** a change touches only a small number of production classes, **Then** the
   build runs only the tests whose tracked dependencies intersect those classes (plus
   any new/modified tests), and completes in less time than a full suite run.
2. **Given** the same change also breaks something a selected test depends on,
   **When** the build runs, **Then** that test executes, fails, and the overall build
   fails — exactly as an unmodified full run would report.
3. **Given** a project's second and later builds, **When** the plugin computes
   selection, **Then** it reuses previously tracked dependency data rather than
   re-deriving dependencies for the entire suite from scratch.

---

### User Story 2 - A real test failure is never silently missed (Priority: P2)

As a developer or reviewer relying on this build to gate a merge, I need certainty
that if the plugin skips a test, that test would not have failed for this change —
the build's trustworthiness as a gate must not degrade compared to running everything.

**Why this priority**: A plugin that saves time but routinely lets a real failure
through would be worse than no plugin at all. Soundness is a strong default here
(Constitution Principle III) — teams are also expected to run their full test suite
portfolio on a regular cadence (recommended: daily) as a complementary safety net,
so this story's bar is "the plugin's own selection is trustworthy," not "the plugin
is the only thing standing between a bug and production."

**Independent Test**: Can be fully tested by making a change that breaks a class,
confirming every test whose tracked dependencies include that class is selected and
runs, and confirming a test whose dependencies do not include it is correctly skipped
only when doing so cannot affect the build's true pass/fail outcome.

**Acceptance Scenarios**:

1. **Given** a change to a production class, **When** the plugin computes selection,
   **Then** every test previously observed to depend on that class is selected,
   without exception.
2. **Given** a change the plugin cannot soundly attribute to specific tests (a
   non-source file, build/dependency configuration, a database migration), **When**
   the plugin computes selection, **Then** the full affected test scope runs rather
   than a narrower guess.
3. **Given** a brand-new test with no tracked dependency history, **When** the plugin
   computes selection, **Then** that test is always selected.

---

### User Story 3 - Understand why a test ran or was skipped (Priority: P3)

As a developer or reviewer looking at a build's results, I want to see, for any test,
why it ran or was skipped — which changed dependency triggered it, or which
conservative rule applied — so I can audit and trust the plugin's decisions rather
than treat them as a black box.

**Why this priority**: Explainability is what lets a team actually trust and keep
using a tool that skips tests, rather than disabling it the first time a decision
looks surprising.

**Independent Test**: Can be fully tested by running a build with the plugin enabled
and confirming that, for any test in the suite, a human-readable reason for its
selection or skip decision is available from the build's own output.

**Acceptance Scenarios**:

1. **Given** a completed build, **When** a developer inspects the results, **Then**
   every skipped test is clearly distinguished from an executed, passing test — never
   reported as if it had passed.
2. **Given** a completed build, **When** a developer inspects a specific test's
   outcome, **Then** they can determine whether it ran because of a dependency match,
   a fallback rule, or being new/modified — without re-running the build or digging
   through internal logs.

---

### User Story 4 - Adoption is safe from the very first build (Priority: P4)

As a team adopting the plugin on an existing project for the first time, I want the
very first build to behave safely — running the full suite — rather than guessing
with no history to go on, so adoption itself never introduces the exact risk the
plugin exists to avoid.

**Why this priority**: A tool that could be unsafe on day one, before it has any
tracked history to rely on, would undermine the trust this whole project is built on
— cold-start safety has to hold as reliably as steady-state safety.

**Independent Test**: Can be fully tested by enabling the plugin on a project with no
prior persisted dependency data and confirming the first build runs the complete
suite while establishing the baseline data subsequent builds will use.

**Acceptance Scenarios**:

1. **Given** a project with the plugin newly enabled and no persisted dependency data,
   **When** the first build runs, **Then** every test in the suite executes.
2. **Given** that first build completes, **When** a second build runs with a small,
   contained change, **Then** the plugin uses the data established by the first build
   to compute a real selection rather than falling back to a full run again.
3. **Given** a project's persisted dependency data becomes inapplicable to the
   current build (e.g. the base reference has diverged in a way the plugin cannot
   reconcile), **When** the plugin detects this, **Then** it runs the full suite and
   re-establishes fresh baseline data, rather than risk an unsound selection.

---

### Edge Cases

- A change touches only a test file, not production code: that test MUST always be
  selected (it is, by definition, changed).
- A change introduces a brand-new test with no prior recorded baseline: it MUST
  always be selected.
- A change touches only non-source artifacts (configuration, resource files, build
  files, database migrations): the full affected suite MUST run, visibly labeled as
  fallback-driven rather than an ordinary dependency match.
- No persisted dependency data exists yet (first-ever build) or it cannot be safely
  applied to the current build: the full suite MUST run rather than guess, and the
  run MUST be used to establish or refresh the persisted data.
- The plugin's own selection computation encounters an internal error: the build MUST
  fail safe by running the full suite, never by silently skipping tests due to an
  internal failure.
- A multi-module Maven reactor build: a change in one module MUST correctly select or
  skip tests in any other module of the same reactor that depends on it.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The plugin MUST provide an opt-in build step that a project can enable
  to run only a computed subset of its test suite for a given build, without
  requiring a separate tool or pipeline outside the project's normal Maven build.
- **FR-002**: The plugin MUST determine each test's dependencies using runtime
  observation of actually-executed code (not static analysis alone), the same
  already-built and tested tracking approach this feature reuses unmodified.
- **FR-003**: The plugin MUST compute which tests are selected for the current build
  by comparing the current changes (uncommitted working-tree changes and/or the
  difference between the current branch and a configured base reference) against
  previously tracked dependency data.
- **FR-004**: The plugin MUST persist its dependency tracking data between builds,
  anchored to a base reference, so that a build's selection computation does not
  require re-deriving dependencies for the entire project from scratch.
- **FR-005**: The plugin MUST apply the same conservative fallback rule this feature
  reuses unmodified: any change outside what dependency tracking can soundly observe
  (non-source files, build/dependency configuration changes, database migrations)
  MUST cause the full affected test scope to run, not a narrower guess.
- **FR-006**: The plugin MUST always select any test that is newly added or whose own
  test file was modified, regardless of whether prior tracked dependency data exists
  for it.
- **FR-007**: When no persisted dependency data exists yet for a project, or existing
  data cannot be safely applied to the current build, the plugin MUST run the full
  test suite rather than guess, and MUST use that run to establish or refresh its
  persisted data for future builds.
- **FR-008**: The plugin's build output MUST clearly distinguish, for every test in
  the suite, whether it ran or was skipped; a skipped test MUST NOT be reported as
  having passed or failed.
- **FR-009**: For every test the plugin selects or skips, the decision MUST be
  traceable to a concrete, human-readable reason (a specific changed dependency, a
  named fallback rule, or "new/modified test"), available from the build's own
  output.
- **FR-010**: The plugin MUST support multi-module Maven reactor builds with
  cross-module dependency attribution — a change in one module MUST correctly select
  or skip tests in any other module of the same reactor that depends on it.
- **FR-011**: A build using the plugin MUST report the same failure outcome the
  project's full, unmodified suite would have reported whenever any executed,
  selected test fails — the plugin MUST NOT alter or suppress a real test failure's
  effect on the build outcome.
- **FR-012**: The plugin MUST NOT require any change to the project's existing test
  code or test framework configuration in order to be adopted — enabling it MUST be
  additive to the existing build behavior, not a replacement requiring migration.

### Key Entities

- **Dependency Index**: the persisted, cacheable record of each test's tracked
  class-level dependencies, anchored to a base reference and updated incrementally
  across builds rather than recomputed from scratch each time.
- **Base Reference**: the git reference (e.g. the default branch) a project's
  persisted dependency index is anchored to; the current build's changes are computed
  relative to this.
- **Selection Decision**: for one test in one build, whether it ran or was skipped,
  and the concrete reason (a specific changed dependency, a named fallback rule, or
  new/modified test).
- **Build Report**: the plugin's per-build output — which tests ran, which were
  skipped, and the reason for each — surfaced alongside the project's normal
  Surefire/Failsafe build output.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A build using the plugin on a typical incremental code change completes
  its test execution in measurably less wall-clock time than the full suite would
  take, without changing the build's pass/fail outcome for that change.
- **SC-002**: Zero would-miss cases occur in live use — every test failure that would
  have occurred in a full, unmodified run also causes the same build failure when the
  plugin is enabled.
- **SC-003**: A team can adopt the plugin on an existing project by enabling one
  opt-in build step, with no required changes to existing test code or CI
  configuration structure.
- **SC-004**: For any skipped test, a developer can determine why it was skipped
  using only the build's own output, without re-running the build or inspecting
  internal logs.
- **SC-005**: A PR/feature-branch build completes its selection computation by reading
  previously-tracked dependency data, without itself re-deriving dependency data for
  the test suite — that cost is paid only by the base-reference build that keeps the
  tracked data fresh, not by every build that uses it.
- **SC-006**: A project's first-ever build with the plugin enabled runs the full
  suite safely, never skipping a test for lack of baseline data.

## Assumptions

- The target project builds with Maven and uses JUnit 5 (via Surefire/Failsafe) as
  its test framework; other build tools are out of scope for this feature.
- "Current changes" means either uncommitted working-tree changes (local development
  use) or the diff between the current branch/PR and a configured base reference (CI
  use) — both are supported, selected by how the plugin is invoked.
- The plugin is adopted per-project by the team that owns it; it is not a hosted or
  SaaS service, and it does not share tracked data across projects.
- This feature builds directly on `blastradius-core`'s already-built and tested
  dependency-tracking mechanism and selection rules (dependency match, fallback,
  new/modified-test) — it reuses that mechanism unmodified rather than reimplementing
  it. Soundness is treated as a strong default (Constitution Principle III), not an
  absolute one: adopting teams are expected to complement the plugin with a regular
  (recommended: daily) full test suite portfolio run.
- No machine-learning component is involved in this feature's selection logic, per
  the constitution's Deterministic Core Before ML principle.
- The persisted dependency index's exact format, storage location, and invalidation
  triggers are implementation decisions for the planning phase, not fixed by this
  specification.
