# Implementation Plan: Shadow-Mode Test Selection Validator

**Branch**: `main` (no feature branch — git extension not installed in this SpecKit setup) | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-shadow-mode-validator/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

A standalone Java/Maven CLI tool that replays a real target project's git history: for each of
an operator-chosen window of recent commit pairs, it computes which tests dependency-based
selection would run (dynamic, class-level, checksum-based dependency tracking via a custom
`java.lang.instrument` agent), always executes the target project's real, unmodified test suite
to get ground truth, confirms any failure with a single re-run to rule out flakiness, and
produces a single JSON report with a PASS/FAIL verdict (PASS iff zero would-miss cases) and an
execution-savings summary. This is the Constitution's Principle V (Shadow-Mode Before Gating)
applied to the entire Blastradius product idea: nothing else gets built until this produces a
PASS with meaningful savings on real data.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: JGit (`org.eclipse.jgit`) for git history/diffing; a custom
`java.lang.instrument`-based dependency-tracking agent (no external library — see
research.md #1); Jackson (`jackson-databind`) for JSON report serialization; JUnit 5
(Jupiter) as this project's own test framework.

**Storage**: Local filesystem only — a single JSON report file per run (research.md #6). No
database; no cross-run persistence in this slice.

**Testing**: JUnit 5, with small purpose-built fixture Maven/JUnit-5 projects under
`src/test/resources/fixture-projects/` for integration-level tests of the harness end-to-end
(commit-pair analysis, agent attachment, report generation), per Constitution Principle I.

**Target Platform**: JVM (Linux/macOS development and CI environments); a CLI tool invoked
locally or from a scheduled job, requiring JDK 21+ and Maven available on the host.

**Project Type**: Single project — standalone CLI/library tool (not the Maven plugin itself;
see spec.md Assumptions — CI integration is an explicitly later, contingent phase).

**Performance Goals**: The dependency-tracking agent's overhead on the target project's own
test execution MUST stay small enough not to distort the run-cost picture the validator exists
to produce — target no more than ~20% wall-clock overhead versus the target project's native
`mvn test` time, exclusive of the confirmation re-run cost (FR-013) which is intentionally
additional.

**Constraints**: MUST NOT mutate the target project's committed repository state — commit
checkouts during analysis MUST be non-destructive and reversible (e.g., a scratch working
copy/worktree), consistent with treating a real external project's history as read-only
ground truth. Full-suite-plus-confirmation execution at every analyzed commit pair is the
dominant cost driver, which is why FR-012 bounds the window rather than analyzing full history.

**Scale/Scope**: One target project per run; an operator-configured window of the most recent
N commit pairs (tens to low hundreds, realistically, given full-suite-per-pair cost) — see
quickstart.md for how N is chosen.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. TDD (NON-NEGOTIABLE) | PASS | Every module (agent/tracking, git traversal, selection decision, verdict/comparison, report) is independently unit-testable; fixture Maven projects enable integration-level red-green-refactor for the end-to-end harness. Enforced at task-breakdown and implementation time. |
| II. Clean Code & Simplicity | PASS | No speculative abstraction: single build-tool target (Maven only), single tracking mechanism (no pluggable backend interface invented ahead of a second real need), no plugin packaging in this slice. |
| III. Safety Over Speed | PASS | This feature's entire purpose is enforcing safety-over-speed on the *product*: would-miss must be zero for PASS, fallback rules force full selection on non-source changes (FR-006), confirmation re-runs protect the verdict from flaky-test false failures (FR-013). |
| IV. Deterministic Core Before ML | PASS | No ML anywhere in this slice (spec Assumptions, explicit). Selection is pure dependency-checksum matching plus named fallback rules. |
| V. Shadow-Mode Before Gating (NON-NEGOTIABLE) | PASS | This feature *is* the shadow-mode mechanism — it never gates or skips a real test run outside its own analysis (spec Assumptions), and its entire output exists to earn (or deny) the deterministic core's promotion out of shadow mode later. |
| VI. Explainability | PASS | `SelectionDecision.reason` and `WouldMissCase.selectionReason` (data-model.md) make every decision traceable to a concrete cause; the report contract forbids opaque results. |
| VII. Maintainable, Modern Foundations | PASS | JDK 21 LTS, JUnit 5 Platform-native, JGit (actively maintained), no Security Manager or deprecated attach mechanisms — the custom agent uses the standard, current `java.lang.instrument` API. |

No violations. Complexity Tracking is not needed for this plan.

## Project Structure

### Documentation (this feature)

```text
specs/001-shadow-mode-validator/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── cli-and-report-contract.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
pom.xml

src/main/java/io/github/baokhang83/blastradius/validator/
├── cli/            # CLI entrypoint, argument parsing, exit-code mapping
├── git/            # JGit-based commit-window resolution and per-pair diffing
├── build/          # Invokes the target project's own `mvn test`/`verify` as a subprocess,
│                   # with the tracking agent attached via JAVA_TOOL_OPTIONS (revised from
│                   # argLine during T061 real-project validation — see research.md §2);
│                   # parses Surefire/Failsafe XML reports for ground truth
├── tracking/       # The java.lang.instrument agent + JUnit 5 TestExecutionListener that
│                   # attributes loaded classes (+ checksums) to the currently-running test
├── selection/       # The deterministic selection engine: dependency-match vs. fallback-rule
│                   # (FR-006) vs. new/modified-test (FR-007) decisions — the core being
│                   # validated
├── verdict/        # Would-miss comparison, single-re-run flaky confirmation (FR-013),
│                   # overall PASS/FAIL computation
└── report/         # AnalysisReport model, JSON serialization (Jackson), rendered text summary

src/test/java/io/github/baokhang83/blastradius/validator/
└── [mirrors src/main structure — unit tests per package]

src/test/resources/fixture-projects/
└── [small, purpose-built Maven/JUnit-5 projects used by integration tests, per Constitution
    Principle I — not the real target project, which is supplied externally at runtime]
```

**Structure Decision**: Single Maven project (matches Project Type above), using standard
Maven source layout (`src/main/java`, `src/test/java`) rather than the template's generic
`src/models/services/cli/lib` shape — consistent with how `mnemo-cache` is structured, and
because this is a plain JVM CLI/library, not a web or mobile application. Package-per-concern
under `validator/` keeps each module small and independently testable per Principle II, mapping
directly onto the pipeline described in the Summary: `git` → `build`/`tracking` → `selection` →
`verdict` → `report`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations — this section is intentionally empty.
