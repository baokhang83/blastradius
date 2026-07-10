---

description: "Task list for CI-Gating Maven Plugin implementation"
---

# Tasks: CI-Gating Maven Plugin

**Input**: Design documents from `/specs/002-ci-gating-plugin/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md,
contracts/mojo-and-index-contract.md, quickstart.md

**Tests**: Included and REQUIRED for every unit of genuinely new logic — Constitution
Principle I (Test-Driven Development) is NON-NEGOTIABLE for this project. The one exception
granted this session (relaxed TDD ceremony) was scoped specifically to bug fixes in the
already-shipped validator and does not carry forward here — this is "the real engine
implementation" the user explicitly reserved full TDD for. The Setup phase's module
*extraction* tasks are the one deliberate exception: they move already-proven, already-tested
code verbatim (no behavior change), so there is no new red-green-refactor cycle to run — the
existing tests moving with the code and staying green *is* the verification.

**Organization**: Tasks are grouped by user story (spec.md, P1-P4) to enable independent
implementation and testing of each story.

**Revision note**: This version incorporates remediation from the `/speckit-analyze` pass on
2026-07-09 — T026 and T029 were added to close a zero-coverage gap for FR-010 (multi-module
reactor support had no task exercising the *plugin's own* live behavior, only
`blastradius-core`'s already-proven tracking mechanism), and T050 was added to close a
zero-coverage gap for the "internal error during selection computation" Edge Case in spec.md
(distinct from T049's configuration-validation errors). This caused a renumbering of every task
from the original T026 onward. `plan.md` and `spec.md` (SC-005) were also corrected in the same
pass for a stale two-mode description and a wording mismatch, respectively — see
`specs/002-ci-gating-plugin/` git history / SESSION.md for the analysis report itself.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Exact file paths are included in every description
- Brief `(depends on ...)` notes are included inline where a task cannot start until a
  specific prior task's file exists

## Path Conventions

Multi-module Maven reactor (per plan.md Structure Decision):
- `blastradius-core/src/main/java/io/github/baokhang83/blastradius/core/{tracking,selection,git}/`
- `blastradius-validator/src/main/java/io/github/baokhang83/blastradius/validator/{cli,git,build,verdict,report}/`
  (unchanged package name for validator-specific code; `tracking`/`selection` removed here,
  now imported from `blastradius-core`)
- `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/{mojo,track,index,diff,report}/`
- Tests mirror each module's `src/main` structure under its own `src/test`

---

## Phase 1: Setup

**Purpose**: Convert the single-module `blastradius-validator` project into a multi-module
reactor, extracting the already-proven `tracking`/`selection` engine into a new shared
`blastradius-core` module (Constitution Principle II — justified now by this feature being the
second real consumer), and scaffolding the new `blastradius-maven-plugin` module. All moves in
this phase are mechanical (no behavior change) — verification is "existing tests still pass
unchanged," not new red-green-refactor cycles.

- [X] T001 Convert root `pom.xml` into a parent POM (`<packaging>pom</packaging>`,
      `<modules>blastradius-core, blastradius-validator, blastradius-maven-plugin</modules>`),
      moving the existing shared `<properties>` (`maven.compiler.release=21`, JGit/Jackson/
      JUnit versions) and `<dependencyManagement>` (junit-bom) up from the current
      single-module `pom.xml` unchanged
- [X] T002 [P] Create `blastradius-core/pom.xml` (packaging=jar; JDK 21; deps: JGit, Jackson,
      JUnit 5 test scope) with the same `maven-shade-plugin` + `maven-jar-plugin`
      configuration blastradius-validator's current `pom.xml` already has (early
      `process-test-classes`-phase jar build, `Premain-Class` manifest transformer, the
      `META-INF/*.SF|.DSA|.RSA` signature-stripping filter, and the `default-jar` →
      `phase=none` override) — `blastradius-core` needs its own standalone, runnable
      `-javaagent` jar for its own integration tests and for `blastradius-maven-plugin`'s
      track subprocess to attach directly, independent of `blastradius-validator`
- [X] T003 [P] Move the `tracking/` package (`DependencyRecord`, `DependencyRecordReader`,
      `DependencyRecordWriter`, `DependencyTrackingAgent`, `TestBoundaryListener`,
      `TestIdentity`) from
      `src/main/java/io/github/baokhang83/blastradius/validator/tracking/` to
      `blastradius-core/src/main/java/io/github/baokhang83/blastradius/core/tracking/`,
      updating the package declaration in each file; move
      `DependencyRecordIoTest`, `DependencyTrackingAgentTest`, `TestBoundaryListenerTest`,
      `TestIdentityTest` to `blastradius-core/src/test/java/.../core/tracking/`; run
      `blastradius-core`'s tests and confirm all four suites pass unchanged (depends on T002)
- [X] T004 [P] Move the `selection/` package (`DependencyMatchSelector`, `FallbackSelector`,
      `NewOrModifiedTestSelector`, `SelectionDecision`, `SelectionEngine`, `SelectionReason`)
      from `.../validator/selection/` to `blastradius-core/.../core/selection/`, updating
      package declarations; move `DependencyMatchSelectorTest`, `FallbackSelectorTest`,
      `NewOrModifiedTestSelectorTest`, `SelectionEngineTest` to
      `blastradius-core/.../core/selection/`; confirm all four suites pass unchanged
      (depends on T002, T003 — `SelectionEngine` imports `TestIdentity`)
- [X] T005 [P] Move `ChangedFile.java`, `FileKind.java`, and `ChangedFileClassifier.java` (+
      `ChangedFileClassifierTest.java`) from `.../validator/git/` to
      `blastradius-core/.../core/git/`, updating package declarations; `CommitPair.java`,
      `PairStatus.java`, `CommitWindowResolver.java`, `CommitCheckout.java` (+ their tests)
      stay in `blastradius-validator/.../validator/git/` unchanged (validator-only, per
      plan.md Project Structure) (depends on T002)
- [X] T006 Move `DependencyTrackingIntegrationTest.java` from
      `.../validator/tracking/` to `blastradius-core/.../core/tracking/`, updating
      `findOwnAgentJar()` to locate `blastradius-core-*.jar` (built by T002's shade config)
      instead of `blastradius-validator-*.jar`; confirm both integration tests (single-module
      and cross-module reactor attribution) pass unchanged (depends on T002, T003)
- [X] T007 Update `blastradius-validator/pom.xml` (moved from the root, now a module) to add a
      dependency on `blastradius-core`; delete the now-duplicated `tracking/`, `selection/`,
      and moved `git/` source files from `blastradius-validator`; update every import across
      `blastradius-validator`'s remaining source (`cli/RunCommand.java`, `build/*.java`,
      `verdict/*.java`, `git/CommitWindowResolver.java`, `git/CommitCheckout.java`, and their
      tests) from `io.github.baokhang83.blastradius.validator.tracking.*` /
      `.validator.selection.*` to `io.github.baokhang83.blastradius.core.tracking.*` /
      `.core.selection.*`; run the full `blastradius-validator` test suite and confirm all
      remaining tests pass unchanged (depends on T003, T004, T005, T006)
- [X] T008 [P] Create `blastradius-maven-plugin/pom.xml` (packaging=`maven-plugin`; deps:
      `blastradius-core`, `maven-plugin-api`, `maven-plugin-annotations` (provided scope),
      JGit, Jackson, JUnit 5 test scope; `maven-plugin-plugin` for goal-descriptor generation)
      (depends on T002)
- [X] T009 [P] Create the package skeleton under
      `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/`
      (`mojo/`, `track/`, `index/`, `diff/`, `report/`), mirrored under
      `blastradius-maven-plugin/src/test/java/.../plugin/` (depends on T008)
- [X] T010 Run `mvn clean install` from the repository root (full 3-module reactor) and
      confirm every module builds and every existing test (moved in T003-T006, untouched in
      T007) passes, before any new Foundational or User Story work begins (depends on T007,
      T009)

**Checkpoint**: Reactor builds cleanly; `blastradius-core`'s proven engine is available to both
`blastradius-validator` (unchanged behavior) and `blastradius-maven-plugin` (new consumer).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared plugin infrastructure every user story sits on — persisting and
reading the dependency index, deciding whether it's applicable, computing the current build's
changes relative to the base reference, running a track subprocess to (re)build the index, and
the Mojo's own mode-routing skeleton. No story-specific Surefire-filter application, failure
propagation, explainability rendering, or track-triggering behavior yet — this phase only
builds what every one of those needs, mirroring how the validator's own Foundational phase
covered "obtaining confirmed ground truth" before any story-specific selection/verdict logic.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### index/

- [X] T011 [P] Write failing tests for the `DependencyIndex` model + JSON read/write
      (round-trips `anchorCommit`, `builtAt`, `testDependencies` per data-model.md and
      contracts/mojo-and-index-contract.md's schema) in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/index/DependencyIndexIoTest.java`
- [X] T012 [P] Implement `DependencyIndex` + `DependencyIndexReader`/`DependencyIndexWriter`
      (Jackson-based, mirrors `blastradius-core`'s `DependencyRecordWriter`/`Reader` pattern)
      (make T011 pass) in
      `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/index/DependencyIndex.java`
      and `DependencyIndexReader.java`/`DependencyIndexWriter.java` (depends on T011)
- [X] T013 Write failing tests for `IndexApplicability` computation — given a persisted index
      (or none, or an unreadable file, or one whose `anchorCommit` is unreachable in the
      project's git history) and the current build's resolved base commit, assert the correct
      `APPLICABLE`/`MISSING`/`UNREADABLE`/`ANCHOR_UNREACHABLE` status per research.md #3 — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/index/IndexApplicabilityResolverTest.java`
      (depends on T012)
- [X] T014 Implement `IndexApplicabilityResolver` (JGit-based reachability check for
      `anchorCommit`) (make T013 pass) in
      `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/index/IndexApplicabilityResolver.java`
      (depends on T013)

### diff/

- [X] T015 [P] Write failing tests for `CurrentChanges` computation — resolve `baseRef` to a
      commit, determine `currentCommit` and `isBaseRefBuild`, and diff `resolvedBaseCommit`
      against the working tree/branch via `blastradius-core`'s `ChangedFileClassifier` — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/diff/CurrentChangesResolverTest.java`
- [X] T016 Implement `CurrentChangesResolver` (JGit-based; reuses
      `blastradius-core`'s `ChangedFileClassifier`/`ChangedFile`/`FileKind` directly per
      plan.md's Structure Decision) (make T015 pass) in
      `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/diff/CurrentChangesResolver.java`
      (depends on T015)

### track/

- [X] T017 Write failing tests for `TrackRunner` — forks a `mvn test` subprocess against a
      `FixtureProjectBuilder`-style project (extended per T009's test skeleton) with the
      tracking agent attached via `JAVA_TOOL_OPTIONS` (reusing `blastradius-core`'s proven
      mechanism — unique-file-per-JVM, merge-on-read via `DependencyRecordReader.readAll`,
      `TestIdentity.baselineKey()` normalization, unchanged), and converts the result into a
      `DependencyIndex` (class names only, per data-model.md's note that checksums aren't part
      of the index) anchored to the subprocess's target commit — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/track/TrackRunnerTest.java`
      (depends on T003, T006, T012)
- [X] T018 Implement `TrackRunner` (make T017 pass) in
      `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/track/TrackRunner.java`
      (depends on T017)

### report/ (skeleton only — content population is story-specific)

- [X] T019 [P] Write failing tests for the `BuildReport` model + JSON write (round-trips
      `mode`, `indexApplicability`, `decisions`, `selectedCount`, `totalCount`, `updatedIndex`
      per data-model.md/contracts) in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/report/BuildReportIoTest.java`
- [X] T020 Implement `BuildReport` + `BuildReportWriter` (make T019 pass) in
      `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/report/BuildReport.java`
      and `BuildReportWriter.java` (depends on T019)

### mojo/ (routing skeleton — each branch's real behavior is a user story)

- [X] T021 Write failing tests for `SelectMojo`'s mode-routing decision (given
      `CurrentChanges.isBaseRefBuild`, `-Dblastradius.mode`, and `IndexApplicability`, assert
      it resolves to exactly one of `TRACK` / `SELECT` / `FALLBACK` per research.md #1 and
      contracts' three-state table) in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/SelectMojoModeRoutingTest.java`
      (depends on T014, T016)
- [X] T022 Implement `SelectMojo`'s `@Mojo(name = "select")` skeleton — parameter binding
      (`baseRef` required, `indexPath` defaulting to `.blastradius/index.json`,
      `-Dblastradius.mode` optional) and the mode-routing decision only, with each branch's
      body left as a not-yet-implemented call to be filled in by US1/US4 (make T021 pass) in
      `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/mojo/SelectMojo.java`
      (depends on T021)

**Checkpoint**: Foundation ready — index persistence, applicability, current-build diffing,
track subprocess execution, report skeleton, and mode routing all exist and are independently
tested. User story implementation can now begin.

---

## Phase 3: User Story 1 - Get a faster build on an ordinary code change (Priority: P1) 🎯 MVP

**Goal**: When a valid index exists (`SELECT` mode), narrow Surefire/Failsafe to only the
tests whose tracked dependencies intersect the current change, and complete faster than a full
run, without changing the build's pass/fail outcome — correctly whether the adopting project is
single-module or a multi-module reactor (FR-010).

**Independent Test**: Fixture project, two commits (base indexed via T017/T018's `TrackRunner`
+ a small, contained change on top); run the goal; confirm fewer tests executed than the full
suite, and the build's overall result matches an unmodified full run of the same change. Repeat
against a 2-module reactor fixture and confirm cross-module attribution holds live, not just in
`blastradius-core`'s already-proven tracking mechanism.

### Tests for User Story 1

- [X] T023 [P] [US1] Write failing tests asserting `SelectMojo`'s `SELECT` branch calls
      `blastradius-core`'s `SelectionEngine` with the current build's `allTests`,
      `index.testDependencies`, and `CurrentChanges.changedFiles`, and produces one
      `SelectionDecision` per test (reusing the engine exactly as proven — no plugin-specific
      selection logic is written here) in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/SelectModeSelectionTest.java`
      (depends on T022)
- [X] T024 [P] [US1] Write failing tests for `SurefireFilterApplier` — given a set of selected
      `TestIdentity`s, asserts it correctly sets the project's effective Surefire/Failsafe
      `test` filter (equivalent to `-Dtest=`) before the `test` phase executes — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/SurefireFilterApplierTest.java`
- [X] T025 [P] [US1] Write a failing end-to-end integration test: a single-module
      `FixtureProjectBuilder` project with an established index (via `TrackRunner`) and a small
      follow-up change, running the full Maven lifecycle through `test` with the goal bound at
      `process-test-classes`; assert Surefire's own execution report shows fewer tests run
      than the fixture's full count, and only the expected (dependency-matched or
      new/modified) tests ran — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/SelectModeEndToEndTest.java`
      (depends on T017, T018)
- [X] T026 [P] [US1] Write a failing end-to-end integration test against a 2-module reactor
      fixture (`FixtureProjectBuilder.twoModuleReactor`, already proven by T006's moved
      integration test): establish an index across both modules via `TrackRunner`, then a
      change in one module's production class; assert the goal correctly computes a distinct,
      correct Surefire filter for *each* module's own `test`-phase execution — the dependent
      test in the other module is selected, unrelated tests in both modules are skipped
      (FR-010) — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/SelectModeMultiModuleEndToEndTest.java`
      (depends on T017, T018)

### Implementation for User Story 1

- [X] T027 [US1] Implement `SurefireFilterApplier` (make T024 pass) in
      `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/mojo/SurefireFilterApplier.java`
      (depends on T024)
- [X] T028 [US1] Implement `SelectMojo`'s `SELECT` branch for a single module — invoke
      `SelectionEngine`, collect selected `TestIdentity`s, apply them via
      `SurefireFilterApplier` (make T023, T025 pass) in `SelectMojo.java` (depends on T022,
      T023, T027)
- [X] T029 [US1] Extend `SelectMojo`/`SurefireFilterApplier` so the goal — which Maven
      executes once per reactor module — computes and applies the correct, independent filter
      for *each* module it runs in, rather than assuming a single-module invocation (make T026
      pass) in `SelectMojo.java`/`SurefireFilterApplier.java` (depends on T026, T028)

**Checkpoint**: At this point, User Story 1 should be fully functional and independently
testable — a project with an established index gets a genuinely faster, correct build, in both
single-module and multi-module reactor shapes.

---

## Phase 4: User Story 2 - A real test failure is never silently missed (Priority: P2)

**Goal**: Prove, at the live-build integration level (not just `blastradius-core`'s already-
proven unit tests), that narrowing via `SurefireFilterApplier` never suppresses a real test
failure's effect on the overall build outcome, and that the conservative fallback/new-modified
rules apply exactly as proven.

**Independent Test**: Fixture project changes that (a) break a class a selected test depends
on — confirm that test runs, fails, and the Maven build fails; (b) touch only a non-source
file — confirm the full suite runs; (c) add a brand-new test — confirm it's always selected.

### Tests for User Story 2

- [X] T030 [P] [US2] Write a failing integration test: a fixture project change that breaks a
      class a tracked test depends on; run the full lifecycle through `test`; assert the
      dependent test executes, fails, and the overall Maven build reports failure (FR-011) —
      in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/SelectedTestFailurePropagatesTest.java`
      (depends on T028)
- [X] T031 [P] [US2] Write a failing integration test: a fixture project change touching only
      a non-source file (e.g. a resource); assert `SelectMojo` produces a `FALLBACK_NON_SOURCE_CHANGE`-driven
      full selection and every test runs, matching `blastradius-core`'s already-proven
      `FallbackSelector` behavior applied live (FR-005) — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/NonSourceChangeFallbackTest.java`
      (depends on T028)
- [X] T032 [P] [US2] Write a failing integration test: a fixture project change adding a
      brand-new test class with no tracked baseline; assert it is always selected regardless
      of the index's contents (FR-006) — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/NewTestAlwaysSelectedTest.java`
      (depends on T028)

### Implementation for User Story 2

- [X] T033 [US2] Fix any gap T030-T032 reveal in how `SelectMojo`/`SurefireFilterApplier`
      propagate a narrowed Surefire execution's failure into the reactor build's own outcome
      (e.g. exit-code/`MojoFailureException` propagation across the `process-test-classes` →
      `test` phase boundary) in `SelectMojo.java`/`SurefireFilterApplier.java` (make T030-T032
      pass) (depends on T030, T031, T032)

**Checkpoint**: User Stories 1 AND 2 both work independently — faster builds that never weaken
the build's trustworthiness as a gate.

---

## Phase 5: User Story 3 - Understand why a test ran or was skipped (Priority: P3)

**Goal**: Every decision — selected or skipped, in any mode — is traceable to a concrete
reason from the build's own output, without re-running anything.

**Independent Test**: Run a build in `SELECT` mode; confirm the console summary and the
written `BuildReport` JSON both show, per test, whether it ran and why; confirm a skipped test
is never reported as passed.

### Tests for User Story 3

- [X] T034 [P] [US3] Write failing tests for `SelectMojo` populating `BuildReport.decisions`
      from the `SELECT` branch's `SelectionDecision` list (from US1's T028), and writing it
      via T020's `BuildReportWriter` to `.blastradius/last-build-report.json` (FR-008, FR-009)
      — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/report/BuildReportPopulationTest.java`
      (depends on T020, T028)
- [X] T035 [P] [US3] Write failing tests for the console summary renderer — given a
      `BuildReport`, produces the `[blastradius] ...` lines per contracts/mojo-and-index-contract.md
      (mode, index anchor + timestamp, selected/total counts, per-reason breakdown) — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/report/ConsoleSummaryRendererTest.java`
- [X] T036 [P] [US3] Write failing tests for the `-Dblastradius.explain=true` expanded
      per-test listing (every test's `selected`/`reason`/`matchedChangedClass`, not just the
      aggregate summary) (SC-004) in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/report/ExplainListingRendererTest.java`

### Implementation for User Story 3

- [X] T037 [US3] Wire `BuildReport` population into `SelectMojo`'s `SELECT` branch (make T034
      pass) in `SelectMojo.java` (depends on T034)
- [X] T038 [P] [US3] Implement `ConsoleSummaryRenderer` (make T035 pass) in
      `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/report/ConsoleSummaryRenderer.java`
      (depends on T035)
- [X] T039 [P] [US3] Implement the `-Dblastradius.explain=true` expanded listing (make T036
      pass) in
      `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/report/ExplainListingRenderer.java`
      (depends on T036)
- [X] T040 [US3] Wire both renderers into `SelectMojo`'s output step (depends on T037, T038,
      T039)

**Checkpoint**: Every build's decisions are fully auditable from its own output.

---

## Phase 6: User Story 4 - Adoption is safe from the very first build (Priority: P4)

**Goal**: A project's first build (and any build where the index is stale/inapplicable)
behaves safely — `TRACK` on a base-ref build (runs full, refreshes the index for next time),
`FALLBACK` on a PR/branch build with no applicable index (runs full, no subprocess forked) —
never guessing, per research.md #1's cost-aware design.

**Independent Test**: Enable the plugin on a fixture project with no persisted index; run a
simulated base-ref build — confirm the full suite runs and a fresh index is written. Run a
simulated PR build with still no index — confirm the full suite runs and no subprocess is
forked (no index appears). Run a PR build with a small change against the now-established
index — confirm it uses `SELECT` mode (from US1) rather than falling back again.

### Tests for User Story 4

- [X] T041 [P] [US4] Write a failing integration test: a fixture project with no persisted
      index, built as a simulated base-ref build (`isBaseRefBuild = true`); assert the full
      suite runs (`selectedCount = totalCount`) and `TrackRunner` (T018) is invoked, producing
      a fresh `.blastradius/index.json` anchored to the current commit (FR-007) — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/TrackModeFirstBuildTest.java`
      (depends on T018, T022)
- [X] T042 [P] [US4] Write a failing integration test: the same fixture project with no
      persisted index, built as a simulated PR/branch build (`isBaseRefBuild = false`); assert
      the full suite runs but no index file is written and `TrackRunner` is never invoked
      (`FALLBACK` mode) — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/FallbackModeNoIndexTest.java`
      (depends on T018, T022)
- [X] T043 [P] [US4] Write a failing integration test: after T041 establishes an index, a
      follow-up PR build with a small, contained change; assert it resolves to `SELECT` mode
      (not `FALLBACK`) and produces a genuine narrowed selection (US1 behavior), proving the
      index survives and gets used across builds (SC-005) — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/IndexReuseAcrossBuildsTest.java`
      (depends on T028, T041)
- [X] T044 [P] [US4] Write a failing test: a persisted index whose `anchorCommit` is
      unreachable (e.g. history rewritten under it); assert `IndexApplicabilityResolver`
      reports `ANCHOR_UNREACHABLE` and `SelectMojo` correctly falls back rather than crashing
      or misapplying a stale selection — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/UnreachableAnchorFallbackTest.java`
      (depends on T014, T022)

### Implementation for User Story 4

- [X] T045 [US4] Implement `SelectMojo`'s `TRACK` branch — invoke `TrackRunner`, persist the
      resulting index via `DependencyIndexWriter`, leave this build's own Surefire execution
      completely unfiltered (make T041 pass) in `SelectMojo.java` (depends on T018, T022)
- [X] T046 [US4] Implement `SelectMojo`'s `FALLBACK` branch — leave Surefire unfiltered,
      deliberately do not invoke `TrackRunner` (make T042 pass) in `SelectMojo.java` (depends
      on T022)
- [X] T047 [US4] Fix any gap T043/T044 reveal in `IndexApplicabilityResolver`/`SelectMojo`'s
      routing so a valid fresh index is correctly picked up by a later `SELECT` build, and an
      unreachable-anchor index is correctly treated as inapplicable (make T043, T044 pass)
      (depends on T043, T044, T045, T046)

      **Remediation note (2026-07-10)**: T041's own live-build integration test revealed that
      `TrackRunner`'s forked subprocess (running the adopting project's own `mvn test`, agent
      attached) executes against the *same* pom the goal is bound in — without a guard, that
      subprocess's own `SelectMojo` execution also resolves `TRACK` (same commit, same
      `baseRef`) and forks *its own* nested subprocess, recursing without bound. This is what
      exhausted host memory in a prior session (a fixture-test-only timeout/kill mitigation had
      already been added defensively, but did not address the recursion itself). Fixed by a new
      `blastradius.trackChild` guard parameter, set only by `TrackRunner`'s own subprocess
      invocation, that makes a nested `SelectMojo.execute()` no-op immediately — safe because
      the tracking data comes entirely from the `-javaagent`, not from the goal's own logic.
      T043's own test also had an unrelated ordering bug (an uncommitted plugin-binding pom.xml
      edit got swept into a later fixture commit by `git add .`, misclassified as a NON_SOURCE
      change, spuriously triggering the fallback rule) — fixed in the test itself. All four
      tests plus the full `blastradius-maven-plugin` suite (42 tests) pass green with no
      leftover subprocesses.

**Checkpoint**: All four user stories are independently functional. Adoption is safe on day
one, and stays safe if the index ever goes stale.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, end-to-end validation against a real project, and error-handling
robustness that spans multiple stories.

- [X] T048 [P] Write `blastradius-maven-plugin/README.md` mirroring quickstart.md — adoption
      snippet, first-build vs. later-build behavior, auditing a decision, why it's safe to
      trust (reused, tested mechanism + the recommended daily full-suite-portfolio
      complement — see quickstart.md's "Why this is safe to trust", updated per the
      constitution v2.0.0 amendment removing Principle V)
- [X] T049 [P] Write failing tests, then implement, graceful error handling for malformed
      plugin configuration (missing `baseRef`, an `indexPath` outside the project directory, a
      target project that isn't a git repository at all) — the goal MUST fail with a clear
      message, distinct from a normal `FALLBACK`/`TRACK` condition, in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/InvalidConfigurationTest.java`
      and corresponding fixes in `SelectMojo.java`

      **Note**: missing `baseRef` and a non-git target already failed with a sufficiently clear
      message pre-existing (Maven's own `required = true` parameter validation, and
      `CurrentChangesResolver`'s `IllegalStateException` message respectively) — the latter is
      now also explicitly caught and re-wrapped as a `MojoExecutionException` for a clean Mojo
      failure instead of a raw stack trace. Only the `indexPath`-outside-project-directory case
      needed a genuinely new check: `SelectMojo.execute()` now normalizes the resolved index
      path and rejects it before use if it escapes the reactor root.
- [X] T050 [P] Write a failing test that injects an unexpected internal fault into
      `SelectMojo`'s `SELECT`-branch computation (e.g. a corrupted intermediate state or a
      simulated JGit exception during diffing, distinct from T049's predictable configuration
      errors), then implement a catch-all fail-safe so the build falls back to running the
      full, unfiltered suite rather than crashing or silently skipping tests, per spec.md's
      Edge Case "the plugin's own selection computation encounters an internal error" — in
      `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/SelectMojoInternalErrorFallbackTest.java`
      and corresponding fixes in `SelectMojo.java` (depends on T028)

      **Note**: fault injected via a persisted index with a duplicate `TestIdentity` key —
      valid JSON, reachable anchor (so `IndexApplicabilityResolver` reports `APPLICABLE` and
      routes to `SELECT`), but `DependencyIndex.testDependenciesByTest()`'s
      `Collectors.toUnmodifiableMap` throws on the duplicate once `computeDecisions` actually
      consumes it — a fault genuinely internal to the SELECT branch, not a config problem T049
      already covers. Added `IndexApplicability.Status.INTERNAL_ERROR` (not a property of the
      index — set only by this catch-all) so the existing `BuildReport.forFallback`/
      `ConsoleSummaryRenderer` machinery could report it without a parallel code path.
      `runSelect`'s try/catch wraps everything through `SurefireFilterApplier.apply`, which
      runs last, so a fault is always caught before any filter is ever applied.
- [X] T051 Run `mvn clean install` from the repository root and confirm the full 3-module
      reactor builds, installs, and every test across all three modules passes
      (147 tests, 0 failures, 0 errors, 0 skipped; no leftover subprocesses)
- [X] T052 Run quickstart.md validation end-to-end: install `blastradius-maven-plugin` into a
      real (or realistic fixture) Maven/JUnit-5 project, run a simulated base-ref build to
      establish an index, then a PR-style build with a real, contained code change, and
      confirm the observed behavior matches quickstart.md exactly — this is this feature's own
      analog of the validator's T061, proving the *live* mechanism works end-to-end, not just
      in isolated unit/integration tests

      **Validated (2026-07-10)**: a from-scratch standalone Maven/JUnit-5 project (2 classes, 2
      independent tests, `<baseRef>main</baseRef>` — not reusing `FixtureProjectBuilder`).
      Base-ref build on `main`: `TRACK`, full suite ran (2/2), `.blastradius/index.json`
      written with real per-test dependency data. Feature-branch build after a small, contained
      change to one production class: `SELECT`, console read exactly
      `[blastradius] SELECT — index built from 4e02156 (...)` /
      `1 / 2 tests selected (50.0% skipped)` /
      `dependency-matched: 1, new-or-modified: 0, fallback: 0` — matching quickstart.md's
      documented format verbatim. Only the dependency-matched test ran (confirmed absent from
      Surefire's own test list, not merely "skipped"); `-Dblastradius.explain=true` and
      `.blastradius/last-build-report.json` both showed the correct per-test reasoning
      (FR-008/FR-009). `BUILD SUCCESS` both builds; no leftover subprocesses.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately. Establishes the multi-module
  reactor and moves already-proven code; nothing here is new behavior.
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories.
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion.
  - US1 (P1) has no dependency on US2/US3/US4 and is the suggested MVP.
  - US2 (P2) depends on US1's `SelectMojo` SELECT branch existing (T028) to have something to
    integration-test failure-propagation against — not independent of US1's *code*, though its
    *value proposition* (trust) is independently meaningful.
  - US3 (P3) depends on US1's T028 (needs real `SelectionDecision`s to populate a report from).
  - US4 (P4) depends only on Foundational (T018, T022) for its own TRACK/FALLBACK branches, but
    T043 (proving index reuse) depends on US1's T028 too.
- **Polish (Phase 7)**: Depends on all four user stories being complete.

### Within Each User Story

- Tests MUST be written and observed failing before implementation (Constitution Principle I).
- Foundational skeletons before story-specific branch logic.
- Story complete (checkpoint) before moving to the next priority, though US2/US3 in practice
  extend the same `SelectMojo.java` file US1 establishes rather than being fully separable —
  noted above, not hidden.

### Parallel Opportunities

- T002, T008 (independent module `pom.xml` creation) can run in parallel once T001 lands.
- T003, T004, T005 (independent package moves) can run in parallel once T002 lands.
- Within Foundational: T011/T012 (index io), T015/T016 (diff), T019/T020 (report skeleton) are
  independent of each other and can proceed in parallel; T017/T018 (track) depends on T012.
- Within US1: T025 (single-module end-to-end) and T026 (multi-module end-to-end) are
  independent tests and can run in parallel.
- Within US2: T030, T031, T032 are independent integration tests and can run in parallel.
- Within US3: T034/T037, T035/T038, T036/T039 are three independent test+implementation pairs.
- Within US4: T041, T042, T044 are independent; T043 depends on T041.

---

## Parallel Example: Foundational Phase

```bash
# Launch independent Foundational sub-areas together once Setup (T001-T010) is complete:
Task: "Write failing tests for DependencyIndex model + JSON read/write in blastradius-maven-plugin/src/test/.../index/DependencyIndexIoTest.java"
Task: "Write failing tests for CurrentChanges computation in blastradius-maven-plugin/src/test/.../diff/CurrentChangesResolverTest.java"
Task: "Write failing tests for the BuildReport model + JSON write in blastradius-maven-plugin/src/test/.../report/BuildReportIoTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (multi-module extraction)
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: a fixture project with a pre-seeded index genuinely runs fewer tests
   on a small change, with the same build outcome as an unmodified full run — in both a
   single-module and a 2-module reactor fixture
5. This alone is not yet safe to recommend for real adoption — US4's cold-start safety and
   US2's failure-propagation proof are what make it trustworthy, not optional polish

### Incremental Delivery

1. Setup + Foundational → reactor builds, shared infrastructure ready
2. US1 → real speedup demonstrated on a pre-seeded index, single- and multi-module (MVP, but
   not yet adoption-ready)
3. US2 → trust proven at the live-build level, not just in blastradius-core's own unit tests
4. US3 → decisions are auditable
5. US4 → cold-start and stale-index safety, closing the loop that makes real adoption safe
6. Polish → real-project validation (T052), this feature's own T061 analog

### Solo Execution Note

Built by a single maintainer, consistent with `specs/001-shadow-mode-validator/tasks.md`'s own
note — the "Parallel Opportunities" above identify where task order is flexible, not where
headcount should be added.

---

## Notes

- `[P]` tasks touch different files with no dependency on an incomplete task.
- `[Story]` labels map tasks to spec.md's user stories for traceability.
- Every implementation task's test must be written and observed failing first (Constitution
  Principle I, NON-NEGOTIABLE) — except Phase 1's mechanical code moves, which carry their
  existing passing tests with them rather than starting a new red-green-refactor cycle.
- No speculative abstraction: `blastradius-core` contains only what US1-US4 concretely need
  today (Constitution Principle II) — no pluggable build-tool/VCS backend, no premature
  multi-index or multi-project support.
- Stop at any checkpoint to validate a story independently before continuing.
