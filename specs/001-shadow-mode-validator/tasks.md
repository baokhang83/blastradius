---

description: "Task list for Shadow-Mode Test Selection Validator implementation"
---

# Tasks: Shadow-Mode Test Selection Validator

**Input**: Design documents from `/specs/001-shadow-mode-validator/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/cli-and-report-contract.md, quickstart.md

**Tests**: Included and REQUIRED for every unit — Constitution Principle I (Test-Driven
Development) is NON-NEGOTIABLE for this project: a failing test precedes every
implementation task. This is not the template's default; it is mandated by
`.specify/memory/constitution.md`.

**Organization**: Tasks are grouped by user story (spec.md, P1-P4) to enable independent
implementation and testing of each story.

**Revision note**: This version incorporates remediation from the `/speckit-analyze`
pass on 2026-07-08 — a `CommitCheckout` component was added to Foundational (closing a
zero-coverage gap for plan.md's non-destructive-checkout constraint), the tracking
integration test was broadened to cover multi-module reactors (closing FR-011's
coverage gap), and `CommitPair`'s field timing was clarified (T007). This caused a
renumbering of all tasks from the original T019 onward (+2).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Exact file paths are included in every description
- Brief `(depends on ...)` notes are included inline where a task cannot start until a
  specific prior task's file exists, so each task is executable without re-deriving order
  from the Dependencies section

## Path Conventions

Single Maven project (per plan.md Structure Decision):
- Main: `src/main/java/io/github/baokhang83/blastradius/validator/{cli,git,build,tracking,selection,verdict,report}/`
- Tests: `src/test/java/io/github/baokhang83/blastradius/validator/...` (mirrors main)
- Test support: `src/test/java/io/github/baokhang83/blastradius/validator/testsupport/`

---

## Phase 1: Setup

**Purpose**: Project initialization and shared test infrastructure

- [X] T001 Create Maven project at repository root: `pom.xml` targeting Java 21
      (`maven.compiler.release=21`), with dependencies on JGit (`org.eclipse.jgit`),
      Jackson (`jackson-databind`), and JUnit 5 (Jupiter) test scope
- [X] T002 [P] Create the package skeleton under
      `src/main/java/io/github/baokhang83/blastradius/validator/` (`cli/`, `git/`,
      `build/`, `tracking/`, `selection/`, `verdict/`, `report/`) mirrored under
      `src/test/java/io/github/baokhang83/blastradius/validator/`, per plan.md Structure
      Decision
- [X] T003 [P] Implement `FixtureProjectBuilder` test-support utility that
      programmatically scaffolds a minimal Maven/JUnit-5 project into a `@TempDir` and
      commits successive changes via JGit (so tests can construct exact dependency/change
      scenarios without a checked-in nested git repo). MUST also support optionally
      scaffolding a 2-module Maven reactor (`moduleA` + `moduleB`, where `moduleB`
      depends on `moduleA`), for tests that need to verify cross-module behavior (FR-011)
      in `src/test/java/io/github/baokhang83/blastradius/validator/testsupport/FixtureProjectBuilder.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared pipeline every user story sits on — git traversal, non-destructive
commit checkout, the dependency-tracking agent, driving the target project's own build,
and obtaining confirmed ground truth. No story-specific selection or verdict logic yet.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### git/

- [X] T004 [P] Write failing tests for the `TestIdentity` value type (equality/identity
      semantics for className + nullable methodName) in
      `src/test/java/io/github/baokhang83/blastradius/validator/tracking/TestIdentityTest.java`
- [X] T005 [P] Implement `TestIdentity` (make T004 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/tracking/TestIdentity.java`
      (depends on T004)
- [X] T006 [P] Write failing tests for the `CommitPair` and `ChangedFile` value types,
      including default `status=ANALYZED` and null `exclusionReason` on construction, in
      `src/test/java/io/github/baokhang83/blastradius/validator/git/CommitPairTest.java`
- [X] T007 [P] Implement `CommitPair` and `ChangedFile` per data-model.md's full shape —
      including `status` (defaulting to `ANALYZED`) and nullable `exclusionReason` as
      intrinsic fields, not added later — (make T006 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/git/CommitPair.java` and
      `ChangedFile.java` (depends on T006)
- [X] T008 Write failing tests for `CommitWindowResolver` — given a `FixtureProjectBuilder`
      repo with N commits, resolve the most recent window as ordered `CommitPair`s — in
      `src/test/java/io/github/baokhang83/blastradius/validator/git/CommitWindowResolverTest.java`
      (depends on T003, T007)
- [X] T009 Implement `CommitWindowResolver` (JGit-based) (make T008 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/git/CommitWindowResolver.java`
      (depends on T008)
- [X] T010 [P] Write failing tests for `ChangedFileClassifier` (classify `JAVA_SOURCE` vs
      `NON_SOURCE` per FR-006, resolve `changedClassName` from path) in
      `src/test/java/io/github/baokhang83/blastradius/validator/git/ChangedFileClassifierTest.java`
      (depends on T007)
- [X] T011 [P] Implement `ChangedFileClassifier` (make T010 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/git/ChangedFileClassifier.java`
      (depends on T010)
- [X] T012 Write failing tests for `CommitCheckout` — JGit-based; materializes one
      commit's tree into an isolated scratch worktree/clone, never touching the target
      repo's current branch or HEAD (per plan.md's non-destructive-checkout constraint) —
      in `src/test/java/io/github/baokhang83/blastradius/validator/git/CommitCheckoutTest.java`
      (depends on T003, T009)
- [X] T013 Implement `CommitCheckout` (make T012 pass; the test MUST assert the target
      repo's original HEAD/branch/working tree is byte-identical before and after a full
      checkout+cleanup cycle) in
      `src/main/java/io/github/baokhang83/blastradius/validator/git/CommitCheckout.java`
      (depends on T012)

### tracking/

- [X] T014 Write failing tests for `DependencyTrackingAgent`'s class-load + bytecode-
      checksum recording (given classes loaded during a run, assert recorded
      name+checksum pairs) in
      `src/test/java/io/github/baokhang83/blastradius/validator/tracking/DependencyTrackingAgentTest.java`
- [X] T015 Implement the `java.lang.instrument` agent (`premain` + `ClassFileTransformer`
      + SHA-256 checksum computation per research.md #1) (make T014 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/tracking/DependencyTrackingAgent.java`
      (depends on T014)
- [X] T016 Write failing tests for `TestBoundaryListener` (JUnit 5
      `TestExecutionListener` marking the currently-executing test via `ThreadLocal` so
      class loads can be attributed to it) in
      `src/test/java/io/github/baokhang83/blastradius/validator/tracking/TestBoundaryListenerTest.java`
- [X] T017 Implement `TestBoundaryListener` (make T016 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/tracking/TestBoundaryListener.java`
      (depends on T005, T015, T016)
- [X] T018 [P] Write failing tests for `DependencyRecordWriter`/`DependencyRecordReader`
      (the agent's recorded `{test -> [(class, checksum)]}` data persisted to and read
      back from a file, since the agent runs in a separate subprocess JVM) in
      `src/test/java/io/github/baokhang83/blastradius/validator/tracking/DependencyRecordIoTest.java`
- [X] T019 [P] Implement `DependencyRecordWriter` and `DependencyRecordReader` (make T018
      pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/tracking/DependencyRecordWriter.java`
      and `DependencyRecordReader.java` (depends on T018)
- [X] T020 Write failing integration tests: (a) attach the agent + listener to a
      single-module `FixtureProjectBuilder` project's own `mvn test` run and assert
      per-test class attribution is correct; (b) repeat against a 2-module reactor
      scenario (per T003) and assert a change to `moduleA`'s class is correctly
      attributed to a dependent test in `moduleB` (FR-011) — in
      `src/test/java/io/github/baokhang83/blastradius/validator/tracking/DependencyTrackingIntegrationTest.java`
      (depends on T003, T015, T017, T019)

### build/

- [X] T021 Write failing tests for `MavenBuildRunner` (invokes `mvn test`/`verify` as a
      subprocess against the working copy `CommitCheckout` materializes, with the
      tracking agent attached via `-DargLine=-javaagent:...` per research.md #2 — later
      revised to `JAVA_TOOL_OPTIONS` during T061's real-project validation; see
      research.md #2 and SESSION.md for why) in
      `src/test/java/io/github/baokhang83/blastradius/validator/build/MavenBuildRunnerTest.java`
      (depends on T003, T013, T015)
- [X] T022 Implement `MavenBuildRunner` (make T021 pass; also satisfies T020 end-to-end)
      in `src/main/java/io/github/baokhang83/blastradius/validator/build/MavenBuildRunner.java`
      (depends on T020, T021)
- [X] T023 [P] Write failing tests for `SurefireReportParser` (parses
      `target/surefire-reports` and `target/failsafe-reports` XML into per-`TestIdentity`
      pass/fail, per research.md #3) in
      `src/test/java/io/github/baokhang83/blastradius/validator/build/SurefireReportParserTest.java`
- [X] T024 [P] Implement `SurefireReportParser` (make T023 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/build/SurefireReportParser.java`
      (depends on T023)
- [X] T025 Write failing tests for `GroundTruthResolver` (runs the build once; for every
      failed test, re-runs just that test once via a targeted `mvn test -Dtest=...` to
      confirm per FR-013; produces `GroundTruthResult` with outcome
      `PASSED`/`CONFIRMED_FAILED`/`FLAKY`) in
      `src/test/java/io/github/baokhang83/blastradius/validator/build/GroundTruthResolverTest.java`
      (depends on T022, T024)
- [X] T026 Implement `GroundTruthResult` and `GroundTruthResolver` (make T025 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/build/GroundTruthResult.java`
      and `GroundTruthResolver.java` (depends on T025)

### cli/ (shared input model)

- [X] T027 [P] Write failing tests for `RunConfig` parsing/validation (`--project-path`
      exists and is a git repo, `--commits` > 0, `--report-out` path writable, per FR-001
      / FR-012) in
      `src/test/java/io/github/baokhang83/blastradius/validator/cli/RunConfigTest.java`
- [X] T028 [P] Implement `RunConfig` (make T027 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/cli/RunConfig.java`
      (depends on T027)

**Checkpoint**: Given a `RunConfig` and a target project, the pipeline can resolve the
commit window, non-destructively check out and diff/classify each pair (including
multi-module reactors), run the target's own build with the tracking agent attached, and
obtain confirmed ground-truth results per test. No selection or verdict logic exists yet
— nothing is user-facing.

---

## Phase 3: User Story 1 - Get a go/no-go verdict for a project's history (Priority: P1) 🎯 MVP

**Goal**: A working CLI that produces a correct PASS/FAIL verdict (with FR-005's
mandatory would-miss detail) for a real commit window, with correct exit codes.

**Independent Test**: Run `blastradius-validator run` against a `FixtureProjectBuilder`
scenario of 5-10 commits and confirm it executes the full suite at every commit, computes
a selection decision per test per commit, and emits one PASS or FAIL verdict with exit
code 0 or 1 respectively.

### Tests for User Story 1

> Write these tests FIRST; confirm they FAIL before implementing.

- [X] T029 [P] [US1] Write failing tests for the dependency-match selection rule (given
      changed classes + a test's recorded dependencies, decide
      selected=true/reason=`DEPENDENCY_MATCH` or selected=false/reason=`NO_MATCH`) in
      `src/test/java/io/github/baokhang83/blastradius/validator/selection/DependencyMatchSelectorTest.java`
- [X] T030 [P] [US1] Write failing tests for the conservative fallback rule (FR-006: a
      `NON_SOURCE` changed file selects every test, reason=`FALLBACK_NON_SOURCE_CHANGE`)
      in
      `src/test/java/io/github/baokhang83/blastradius/validator/selection/FallbackSelectorTest.java`
- [X] T031 [P] [US1] Write failing tests for the new/modified-test-always-selected rule
      (FR-007: reason=`NEW_OR_MODIFIED_TEST`) in
      `src/test/java/io/github/baokhang83/blastradius/validator/selection/NewOrModifiedTestSelectorTest.java`
- [X] T032 [P] [US1] Write failing tests for `WouldMissComparator` (given
      `SelectionDecision`s + `GroundTruthResult`s, produce a `WouldMissCase` for every
      confirmed-failed test that was not selected, per FR-004) in
      `src/test/java/io/github/baokhang83/blastradius/validator/verdict/WouldMissComparatorTest.java`
- [X] T033 [P] [US1] Write failing tests for `VerdictCalculator` (PASS iff
      `wouldMissCases` is empty; FAIL otherwise, per FR-005) in
      `src/test/java/io/github/baokhang83/blastradius/validator/verdict/VerdictCalculatorTest.java`
- [X] T034 [US1] Write failing end-to-end integration test: run the full pipeline against
      two `FixtureProjectBuilder` scenarios — one engineered to PASS, one engineered to
      FAIL (a dependent test deliberately excluded from selection to simulate a
      would-miss) — asserting correct verdict and exit code in
      `src/test/java/io/github/baokhang83/blastradius/validator/EndToEndVerdictIntegrationTest.java`
      (depends on T009, T011, T026, T028)

### Implementation for User Story 1

- [X] T035 [P] [US1] Implement `SelectionDecision` entity + `DependencyMatchSelector`
      (make T029 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/selection/SelectionDecision.java`
      and `DependencyMatchSelector.java` (depends on T029)
- [X] T036 [P] [US1] Implement `FallbackSelector` (make T030 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/selection/FallbackSelector.java`
      (depends on T030)
- [X] T037 [P] [US1] Implement `NewOrModifiedTestSelector` (make T031 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/selection/NewOrModifiedTestSelector.java`
      (depends on T031)
- [X] T038 [US1] Implement `SelectionEngine`, composing the three selectors into one
      per-test `SelectionDecision` (fallback and new/modified-test take precedence over a
      plain no-match) in
      `src/main/java/io/github/baokhang83/blastradius/validator/selection/SelectionEngine.java`
      (depends on T035, T036, T037)
- [X] T039 [P] [US1] Implement `WouldMissCase` entity + `WouldMissComparator` (make T032
      pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/verdict/WouldMissCase.java`
      and `WouldMissComparator.java` (depends on T032)
- [X] T040 [US1] Implement `VerdictCalculator` (make T033 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/verdict/VerdictCalculator.java`
      (depends on T033, T039)
- [X] T041 [US1] Implement the CLI `run` command wiring the full pipeline
      (`RunConfig` → `CommitWindowResolver` → per pair: `CommitCheckout` →
      `ChangedFileClassifier` → `GroundTruthResolver` → `SelectionEngine` →
      `WouldMissComparator` → `VerdictCalculator`) and exit-code mapping (0=PASS, 1=FAIL,
      2=could-not-run per the CLI contract) in
      `src/main/java/io/github/baokhang83/blastradius/validator/cli/RunCommand.java` and
      `Main.java` (depends on T009, T011, T026, T028, T038, T040)
- [X] T042 [US1] Implement minimal `AnalysisReport` JSON serialization via Jackson
      (verdict, `analyzedCommitPairs`, `wouldMissCases` with the full FR-005 detail
      fields) (make T034 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/report/AnalysisReport.java`
      and `ReportWriter.java` (depends on T034, T041)

**Checkpoint**: `blastradius-validator run` produces a correct PASS/FAIL verdict with
FR-005 would-miss detail and correct exit codes against a real target project. **This is
the MVP** — Constitution Principle V's gate can now actually be exercised.

---

## Phase 4: User Story 2 - See execution-savings evidence (Priority: P2)

**Goal**: Pair a PASS verdict with a concrete execution-savings number, so the go/no-go
product decision has real evidence behind it (FR-008 / SC-003).

**Independent Test**: Take a completed run and confirm the report states total test
executions, total selected, resulting proportion skipped, and the fallback-vs-dependency-
match split.

### Tests for User Story 2

- [X] T043 [P] [US2] Write failing tests for `SavingsSummaryAggregator`
      (`totalTestExecutions`, `totalSelected`, `proportionSkipped`,
      `fallbackDrivenSelections` vs `dependencyMatchedSelections`, per FR-008) in
      `src/test/java/io/github/baokhang83/blastradius/validator/report/SavingsSummaryAggregatorTest.java`
- [X] T044 [US2] Write failing integration test: run a `FixtureProjectBuilder` end-to-end
      scenario and assert the report's `savingsSummary` numbers match hand-computed
      expectations in
      `src/test/java/io/github/baokhang83/blastradius/validator/SavingsSummaryIntegrationTest.java`
      (depends on T042, T043)

### Implementation for User Story 2

- [X] T045 [P] [US2] Implement `SavingsSummary` entity + `SavingsSummaryAggregator` (make
      T043 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/report/SavingsSummary.java`
      and `SavingsSummaryAggregator.java` (depends on T043)
- [X] T046 [US2] Wire `SavingsSummaryAggregator` into the CLI run pipeline and
      `AnalysisReport` JSON output (make T044 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/cli/RunCommand.java`
      (extend) and `report/AnalysisReport.java` (extend) (depends on T041, T045)

**Checkpoint**: US1 + US2 both work — a verdict paired with the savings evidence needed
to make the go/no-go product decision.

---

## Phase 5: User Story 3 - Investigate a FAIL verdict in detail (Priority: P3)

**Goal**: Make a FAIL verdict fully actionable — a human-readable rendering of every
would-miss case, and a guarantee (tested at scale) that none are ever summarized away.

**Independent Test**: Take a run with a known FAIL verdict and confirm every would-miss
case appears individually, with commit, test identity, changes, and reasoning, in both
the JSON report and the rendered text summary — including when there are multiple cases.

### Tests for User Story 3

- [X] T047 [P] [US3] Write failing tests for `TextSummaryRenderer` (verdict line,
      per-would-miss-case detail lines, savings lines matching the contract's example
      format in contracts/cli-and-report-contract.md) in
      `src/test/java/io/github/baokhang83/blastradius/validator/report/TextSummaryRendererTest.java`
- [X] T048 [US3] Write failing integration test asserting a FAIL run with MULTIPLE
      would-miss cases has every case present in both the JSON and text output (none
      aggregated or summarized away, per spec.md US3 acceptance scenario 2) in
      `src/test/java/io/github/baokhang83/blastradius/validator/MultiWouldMissIntegrationTest.java`
      (depends on T042, T047)
- [X] T049 [P] [US3] Write failing contract tests asserting `AnalysisReport` JSON
      satisfies every invariant in contracts/cli-and-report-contract.md (verdict/
      wouldMissCases consistency, savings arithmetic, analyzed/excluded pair partition)
      in
      `src/test/java/io/github/baokhang83/blastradius/validator/report/AnalysisReportContractTest.java`

### Implementation for User Story 3

- [X] T050 [US3] Implement `TextSummaryRenderer` (make T047, T048 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/report/TextSummaryRenderer.java`
      (depends on T047)
- [X] T051 [US3] Wire `TextSummaryRenderer` into the CLI (stdout by default,
      `--summary-out` per the CLI contract) in
      `src/main/java/io/github/baokhang83/blastradius/validator/cli/RunCommand.java`
      (extend) (depends on T050)
- [X] T052 [US3] Fix any `AnalysisReport` serialization gaps surfaced by the contract
      tests (make T049 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/report/AnalysisReport.java`
      (extend) (depends on T049)

**Checkpoint**: US1-US3 all independently functional; a FAIL verdict is now fully
actionable via both machine-readable JSON and human-readable text.

---

## Phase 6: User Story 4 - Analysis survives broken commits (Priority: P4)

**Goal**: A single unbuildable commit in a long historical range never aborts the run or
corrupts the verdict (FR-009).

**Independent Test**: Run across a range including at least one commit known not to
build; confirm the run completes, that pair is listed as excluded with a reason, and the
verdict/savings are computed only from the remaining valid pairs.

### Tests for User Story 4

- [X] T053 [P] [US4] Write failing tests for build-failure detection in
      `MavenBuildRunner`/`GroundTruthResolver` (a commit pair whose `mvn test`/`verify`
      invocation fails to build is detected distinctly from an ordinary test failure) in
      `src/test/java/io/github/baokhang83/blastradius/validator/build/BuildFailureDetectionTest.java`
- [X] T054 [US4] Write failing integration test: run the pipeline across a
      `FixtureProjectBuilder` history containing one deliberately unbuildable commit;
      assert the run completes, that pair is excluded with a reason, and verdict/savings
      are computed only from valid pairs in
      `src/test/java/io/github/baokhang83/blastradius/validator/BrokenCommitIntegrationTest.java`
      (depends on T026, T041, T053)

### Implementation for User Story 4

- [X] T055 [P] [US4] Implement build-failure detection logic that sets
      `CommitPair.status=EXCLUDED` and populates `exclusionReason` on an already-existing
      field (make T053 pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/build/MavenBuildRunner.java`
      (extend) — `CommitPair`'s fields already exist per T007; this task adds detection
      logic only, not new fields (depends on T053)
- [X] T056 [US4] Wire exclusion handling into the CLI run loop (catch, log, continue to
      next pair) and into `AnalysisReport`'s `excludedCommitPairs` section (make T054
      pass) in
      `src/main/java/io/github/baokhang83/blastradius/validator/cli/RunCommand.java`
      (extend) and `report/AnalysisReport.java` (extend) (depends on T046, T055)

**Checkpoint**: All four user stories independently functional — the validator is robust
enough to run unattended across a real, imperfect commit history.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T057 [P] Implement flaky-failure reporting (FR-014): surface
      `GroundTruthResult` entries with outcome=`FLAKY` as `FlakyFailure` records in
      `AnalysisReport` and the text summary in
      `src/main/java/io/github/baokhang83/blastradius/validator/verdict/FlakyFailure.java`
      and `report/AnalysisReport.java` (extend)
- [X] T058 [P] Write README usage instructions mirroring quickstart.md in `README.md`
- [X] T059 [P] Add CLI-level tests for exit code 2 (invalid `--project-path`, non-Maven
      target) in
      `src/test/java/io/github/baokhang83/blastradius/validator/cli/RunCommandErrorHandlingTest.java`
- [X] T060 Measure and record tracking-agent overhead against the ~20% target from
      plan.md Technical Context / spec.md SC-006, on a fixture and/or real project run
- [X] T061 Run quickstart.md validation end-to-end against a real, small open-source
      Maven/JUnit-5 project (manual validation step) — PASS, zero would-miss cases,
      across 105 real commit pairs on two projects (commons-io: 5 pairs, 41.2% savings;
      jsoup: 100 pairs, 2.0% savings). See SESSION.md for the full validation journey,
      including three real agent-injection bugs found and fixed against real projects.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion. This is the MVP.
- **User Story 2 (Phase 4)**: Depends on Foundational; also depends on US1's
  `RunCommand`/`AnalysisReport` existing (T041, T042) since it extends them — not
  independent of US1 in implementation, though independently *testable* once built.
- **User Story 3 (Phase 5)**: Same relationship — extends US1's `AnalysisReport`/
  `RunCommand` (T041, T042).
- **User Story 4 (Phase 6)**: Extends US1's `RunCommand` (T041) and, if built, US2's
  report extension (T046).
- **Polish (Phase 7)**: Depends on all desired user stories being complete.

Note: unlike a typical multi-surface feature, US2-US4 here are additive extensions of the
single CLI pipeline US1 establishes (per plan.md's single-project structure), so they are
sequenced after US1 rather than buildable fully in parallel with it — but each remains
independently *testable and demonstrable* once its phase completes, per the Independent
Test criteria above.

### Within Each Phase

- Tests MUST be written and FAIL before implementation (Constitution Principle I,
  NON-NEGOTIABLE)
- Entities/value types before the logic that consumes them
- Foundational pipeline (git → checkout → tracking → build) before any selection/verdict
  logic
- Selection before verdict; verdict before CLI wiring; CLI wiring before report
  serialization
- Story complete (checkpoint reached) before moving to the next priority

### Parallel Opportunities

- T002, T003 (Setup) can run in parallel
- Within Foundational: the `git/` value-type tests (T004, T006), the `tracking/` agent
  test (T014), and the `cli/` `RunConfig` test (T027) have no dependencies on each other
  and can start in parallel; their implementations follow the same parallel shape once
  each test is written
- Within US1: T029-T033 (the three selector tests + `WouldMissComparator` +
  `VerdictCalculator` tests) can be written in parallel; T035-T037 (the three selector
  implementations) can be built in parallel once their respective tests exist
- Within US2/US3/US4: the `[P]`-marked test and entity/aggregator implementation tasks in
  each phase have no cross-dependencies within that phase

---

## Parallel Example: User Story 1

```bash
# Launch the independent selector tests together:
Task: "Write failing tests for dependency-match selection in .../selection/DependencyMatchSelectorTest.java"
Task: "Write failing tests for the fallback rule in .../selection/FallbackSelectorTest.java"
Task: "Write failing tests for new/modified-test selection in .../selection/NewOrModifiedTestSelectorTest.java"

# Once each test exists, its implementation can proceed independently:
Task: "Implement DependencyMatchSelector in .../selection/DependencyMatchSelector.java"
Task: "Implement FallbackSelector in .../selection/FallbackSelector.java"
Task: "Implement NewOrModifiedTestSelector in .../selection/NewOrModifiedTestSelector.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks everything)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: run against a real small target project per quickstart.md;
   confirm a correct verdict and exit code
5. This is the point at which Constitution Principle V's gate can first be exercised for
   real — everything after this is evidence-gathering and ergonomics, not core soundness

### Incremental Delivery

1. Setup + Foundational → pipeline can obtain ground truth, nothing user-facing yet
2. + US1 → **MVP**: a trustworthy PASS/FAIL verdict — validate independently
3. + US2 → savings evidence added — validate independently
4. + US3 → FAIL verdicts become fully actionable — validate independently
5. + US4 → robust against real, imperfect project histories — validate independently
6. + Polish → flaky-failure transparency, docs, error handling, real-world quickstart run

### Solo Execution Note

This project is being built by a single maintainer, not a parallel team — the "Parallel
Team Strategy" section from the template is omitted. The Parallel Opportunities above
still matter: they identify where task order is flexible, not where headcount should be
added.

---

## Notes

- `[P]` tasks touch different files with no dependency on an incomplete task
- `[Story]` labels map tasks to spec.md's user stories for traceability
- Every implementation task's test must be written and observed failing first
  (Constitution Principle I, NON-NEGOTIABLE)
- No speculative abstraction: no pluggable build-tool or VCS backend interfaces exist
  anywhere in this task list — only what Maven + JGit + JUnit 5 concretely require
  (Constitution Principle II)
- Stop at any checkpoint to validate a story independently before continuing
