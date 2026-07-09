# Implementation Plan: CI-Gating Maven Plugin

**Branch**: `main` (no feature branch — git extension not installed in this SpecKit setup) | **Date**: 2026-07-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-ci-gating-plugin/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

A real, installable Maven plugin (`blastradius-maven-plugin`) providing one adopter-facing
goal, `blastradius:select`, that computes dependency-based test selection for the current
build and hands it to Surefire/Failsafe as a standard test filter — actually skipping tests,
not just shadow-reporting on what it would skip. It reuses, unmodified, the exact dependency-
tracking and selection mechanism the shadow-mode validator already proved sound (T061: PASS,
zero would-miss cases, 105 real commit pairs on two real projects) — extracted into a new
shared `blastradius-core` module rather than reimplemented, since this is exactly the second
real consumer that justifies that extraction under Constitution Principle II. The goal
maintains a persisted, cacheable dependency index anchored to a base git reference, refreshed
by an explicit, separate full-suite "track" run rather than by ever attempting to instrument
the live, gated build — a design chosen specifically to avoid reintroducing the `argLine`
fragility research.md #2 of the validator's own spec already found and fixed once.

## Technical Context

**Language/Version**: Java 21 (LTS) — unchanged from the validator.

**Primary Dependencies**: `blastradius-core` (new, extracted from the validator — see Project
Structure); `maven-plugin-api` + `maven-plugin-annotations` (standard, current Maven plugin
SDK, for the `@Mojo`/`@Parameter` model); JGit (already used by the validator, reused here for
base-ref diffing); Jackson (dependency index + build report JSON, matching the validator's
existing report serialization approach); JUnit 5 (Jupiter) as this project's own test
framework.

**Storage**: A local-filesystem, per-project dependency index file (e.g.
`.blastradius/index.json` under the target project's own working directory) — no database, no
hosted service, consistent with the "not a hosted/SaaS service" assumption in spec.md. On
ephemeral CI runners, persisting this file across builds is the adopting team's own CI-cache
concern (the same externality any Maven/Gradle build cache already has) — not something this
plugin manages itself.

**Testing**: JUnit 5, extending the validator's existing `FixtureProjectBuilder` test-support
pattern to also exercise the plugin's goal against small fixture Maven/JUnit-5 projects across
two commits (base + changed), asserting both the "track" and "select" code paths end-to-end,
per Constitution Principle I. `blastradius-core`'s extracted classes keep their existing,
already-green unit tests, moved (not rewritten) into the new module.

**Target Platform**: JVM; Maven 3.6+ (the plugin's own minimum supported Maven version,
consistent with current `maven-plugin-api` baselines); the plugin itself is installed into a
local or remote Maven repository for adopting projects to depend on — this is the first
Blastradius artifact meant to be *installed by someone else's build*, not just run standalone.

**Performance Goals**: Carries forward the validator's proven ≤~20% tracking-agent overhead
target (SC-006 of spec 001) for "track" runs, since they reuse the identical mechanism. For
"select" runs (the common case, and the one this feature's business case rests on), the
plugin's own overhead — reading the persisted index, computing the base-ref diff, composing
the test filter — MUST be negligible (low single-digit seconds) relative to the test time it
saves; SC-001 requires the *net* build time to measurably improve, not just the selection step
itself to be fast.

**Constraints**: MUST NOT require a hosted service or cross-project data sharing (spec.md
Assumptions). MUST NOT alter the adopting project's existing test source or Surefire/Failsafe
configuration beyond what the plugin's own goal execution needs (FR-012). MUST fail safe (full
suite) whenever the persisted index is missing, stale, or inapplicable (FR-007) — never guess.
MUST NOT reattach the dependency-tracking agent to the live, currently-executing build process
being gated — see research.md's Phase 0 decision on why, given the validator's own hard-won
`argLine`/`JAVA_TOOL_OPTIONS` findings.

**Scale/Scope**: One target project per install, one dependency index anchored to one base
reference at a time — matches the validator's "one target project per run" simplicity
(spec.md Assumptions carries this forward; multi-project scope remains explicitly out of
scope, same as the validator).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. TDD (NON-NEGOTIABLE) | PASS | `blastradius-core`'s extracted classes keep their existing tests (moved, not rewritten — no red-green-refactor gap). Every genuinely new class (the Mojo, index persistence, base-ref diffing, build report rendering) gets its own red-green-refactor cycle at task-breakdown/implementation time, same discipline as the validator. |
| II. Clean Code & Simplicity | PASS | The `blastradius-core` extraction is not speculative — it is justified now by a second real, concrete consumer (this plugin) needing the exact same proven mechanism, which is precisely the condition Principle II requires before extracting a shared abstraction. |
| III. Safety Over Speed (NON-NEGOTIABLE) | PASS | FR-005/FR-007/FR-008/FR-011 carry forward the validator's exact conservative rules unchanged: fallback on unsound-to-observe changes, always-select new/modified tests, fail-safe on missing/stale index, never suppress a real failure's effect on build outcome. |
| IV. Deterministic Core Before ML | PASS | No ML anywhere in this feature (spec.md Assumptions, explicit). |
| V. Shadow-Mode Before Gating (NON-NEGOTIABLE) | PASS | This is the feature Principle V exists to eventually permit — and it is permitted specifically because the deterministic core already earned it (T061: zero would-miss, 105 real commit pairs, two real projects). This plan does not re-earn that trust; it exercises it, and does not extend gating to any capability that hasn't separately passed shadow mode (e.g., no ML layer is being smuggled in under this same trust). |
| VI. Explainability | PASS | FR-008/FR-009 and the Build Report entity carry the validator's `SelectionDecision.reason` concept forward unchanged into live use — every skip is traceable, never a bare decision. |
| VII. Maintainable, Modern Foundations | PASS | Same JDK 21 LTS, JUnit 5 Platform, current `maven-plugin-api`/`maven-plugin-annotations` (actively maintained, the standard current mechanism for Maven plugin development) — no deprecated Maven plugin APIs (e.g. no legacy `AbstractMojo` patterns predating the annotation-based model). |

No violations. Complexity Tracking is not needed for this plan. The multi-module restructuring
(extracting `blastradius-core`) is a structural decision serving Principle II, not a violation
requiring justification.

**Post-Phase 1 re-check**: research.md's decisions (subprocess-only agent attachment,
local-filesystem index, commit-anchored applicability) and data-model.md/contracts introduce no
new dependency, no hosted component, and no change to the selection rules themselves — they
only decide *when* and *how* the already-proven rules get applied live. The table above still
holds unchanged after Phase 1 design.

## Project Structure

### Documentation (this feature)

```text
specs/002-ci-gating-plugin/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── mojo-and-index-contract.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

This feature converts the repository from a single Maven module into a multi-module reactor.
The existing validator's proven `tracking/` and `selection/` packages (and the reusable part of
`git/`) move into a new shared module verbatim — moved, not rewritten, keeping their existing
tests green throughout the move (Constitution Principle I: no red-green-refactor gap for code
that's already proven).

```text
pom.xml                              # NEW parent POM (packaging=pom), holds shared
                                      # dependencyManagement (JGit/Jackson/JUnit versions,
                                      # unchanged from today's single-module pom.xml)

blastradius-core/                    # NEW module — the proven engine, extracted
├── pom.xml
├── src/main/java/io/github/baokhang83/blastradius/core/
│   ├── tracking/   # DependencyTrackingAgent, TestBoundaryListener, TestIdentity,
│                   # DependencyRecordWriter/Reader — moved as-is from the validator,
│                   # including the JAVA_TOOL_OPTIONS attachment approach and the
│                   # per-JVM-unique-file + merge design (research.md #2, #003 fixes)
│   ├── selection/  # SelectionEngine, DependencyMatchSelector, FallbackSelector,
│                   # NewOrModifiedTestSelector, SelectionDecision, SelectionReason —
│                   # moved as-is; this is the deterministic core both consumers share
│   └── git/        # ChangedFileClassifier only (JAVA_SOURCE/NON_SOURCE classification
│                   # of a diff) — CommitWindowResolver and CommitCheckout stay in the
│                   # validator, since historical-replay checkout logic has no
│                   # equivalent need in a live, single-commit gating context
└── src/test/java/io/github/baokhang83/blastradius/core/
    └── [existing tracking/selection tests, moved as-is]

blastradius-validator/               # EXISTING validator, now a module, depends on
│                                     # blastradius-core for tracking/ and selection/
├── pom.xml                          # trimmed: only validator-specific deps remain local
└── src/                             # cli/, git/ (CommitWindowResolver, CommitCheckout,
                                      # ChangedFile types), build/, verdict/, report/ —
                                      # unchanged in behavior, now importing from
                                      # blastradius-core instead of defining tracking/
                                      # selection locally

blastradius-maven-plugin/            # NEW module — the real product this feature builds
├── pom.xml                          # packaging=maven-plugin
├── src/main/java/io/github/baokhang83/blastradius/plugin/
│   ├── mojo/       # SelectMojo — the `blastradius:select` goal; routes to one of three
│                   # modes (research.md #1): TRACK (this build IS the base reference,
│                   # or -Dblastradius.mode=track — full suite runs, a subprocess
│                   # refreshes the index), SELECT (a valid index applies — compute
│                   # selection, hand Surefire a -Dtest= filter), or FALLBACK (no valid
│                   # index and not a base-ref build — full suite runs, but no
│                   # subprocess is forked, since this build's commit is a poor index
│                   # anchor anyway)
│   ├── track/      # TrackRunner — forks the target project's own `mvn test` as a
│                   # *separate subprocess*, agent attached via JAVA_TOOL_OPTIONS,
│                   # reusing blastradius-core's tracking mechanism exactly as the
│                   # validator's MavenBuildRunner already proved — deliberately never
│                   # instruments the live, currently-running build being gated
│   ├── index/      # DependencyIndex model + persistence (read/write the local
│                   # .blastradius/index.json), anchor validation (is the persisted
│                   # index still applicable to the current base ref?)
│   ├── diff/       # Computes changed files between the base reference and the
│                   # current build's changes (working tree and/or branch), reusing
│                   # blastradius-core's ChangedFileClassifier
│   └── report/     # Build Report rendering — which tests ran/were skipped and why,
│                   # surfaced in the build's own output (distinct from the validator's
│                   # AnalysisReport, which is shaped around would-miss verdicts this
│                   # feature has no equivalent need for)
└── src/test/java/io/github/baokhang83/blastradius/plugin/
    └── [mirrors src/main structure; integration tests extend FixtureProjectBuilder
        to exercise the goal end-to-end against small fixture projects]
```

**Structure Decision**: Multi-module Maven reactor (parent `pom.xml` + three modules), replacing
today's single-module `blastradius-validator` project. This is the smallest structural change
that lets the plugin depend on the validator's exact proven `tracking`/`selection` code without
duplicating it — duplication would mean the plugin's soundness claim rests on *unvalidated*
copied code, which would quietly undermine the whole premise that this feature inherits T061's
evidence rather than re-earning it. `blastradius-core` contains only what's genuinely shared;
validator-specific mechanics (historical commit-pair replay, subprocess ground-truth resolution,
would-miss verdict computation) stay in `blastradius-validator`, and plugin-specific mechanics
(the live Mojo, index persistence, build-time diffing) stay in `blastradius-maven-plugin`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations — this section is intentionally empty.
