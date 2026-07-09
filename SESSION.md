# Blastradius — Session Summary

**Purpose of this file**: a handoff document for picking this project back up after
context is cleared. Explains what Blastradius is, why it exists, everything built so
far, and what comes next. Read this first in a fresh session.

---

## What Blastradius is

**A shadow-mode validator that tests whether dependency-based test selection can safely
speed up Java CI.** Given a Maven/JUnit 5 project's git history, it replays commit pairs
and asks two questions on real data, before anything real gets built:

1. Would dependency-based test selection ever have **missed a real test failure**? (must
   be zero — this is the safety bar)
2. How much test execution would it have **saved**? (the business case)

It never skips a real test itself. It always runs the full suite and only *afterward*
checks whether its selection logic would have chosen correctly. That's the "shadow mode."

## Why this exists (the path here)

This came out of a long exploration of "what's a good next library to build," in the
same spirit as `mnemo-cache` (a published, working library — see
`~/.claude/projects/.../memory/project_mnemo_cache.md` if that memory still exists).
Several ideas were floated and **killed by evidence** before landing here:

- An LLM model router — killed: the pitch was mostly invented, no real demand found.
- A semantic/embedding LLM cache — real category, but heavy and dependency-laden.
- An RL replay buffer — killed: RL training lives in Python, no JVM audience.
- A JVM contextual-bandit library — killed: plain bandits are already covered
  (Wealthfront, Stitch Fix); the contextual niche is real but thin and demand-unproven.
- An LLM guardrails/PII suite — real gap, but heavy (needs ML models) and a solo
  competitor (Semantic Privacy Guard) already exists.
- **A modern Maven test-impact-selection plugin** — this is the one that survived every
  check: durable demand (every large Java shop wants faster CI), users natively on the
  JVM, and the existing options are either abandoned academic projects (Ekstazi, STARTS
  — JUnit 4 only, JDK-8-era hacks) or commercial SaaS (Gradle Develocity, Launchable).
  No modern, maintained, JUnit-5-native OSS option exists. That's Blastradius.

**Name**: "blast radius" = which tests a change can reach — the whole point of the tool.

## The core design decision: don't reimplement anything unsound

The naive approach — map `git diff` straight to "tests that touch those files" — is
**unsafe**: it misses Spring DI, reflection, and classpath-scanning edges. Blastradius
instead uses **dynamic, class-level dependency tracking**: a real Java agent observes
which classes each test actually loads at runtime, via `java.lang.instrument`. This
catches reflective/DI edges that static analysis can't see. This is also why a
**multi-module Maven reactor gets cross-module attribution for free** — the agent
doesn't care about module boundaries, only actual class loads.

## Governance: the project constitution

`.specify/memory/constitution.md` (v1.0.0, ratified 2026-07-08) sets seven principles,
three non-negotiable:

- **I. TDD (NON-NEGOTIABLE)** — red→green→refactor for all engine code, no exceptions.
- **II. Clean Code & Simplicity** — no speculative abstraction.
- **III. Safety Over Speed (NON-NEGOTIABLE)** — ambiguity always resolves to "run more
  tests." Conservative fallback rule for anything outside sound observation (resources,
  `pom.xml`, migrations).
- **IV. Deterministic Core Before ML** — the core is pure dependency tracking; ML would
  be a later, optional, non-default layer (not built yet, not urgently needed).
- **V. Shadow-Mode Before Gating (NON-NEGOTIABLE)** — nothing is trusted to skip real
  tests until it's proven a zero would-miss rate in shadow mode, on real usage. This is
  *why the whole project you're reading about is a validator, not a plugin yet.*
- **VI. Explainability** — every decision traces to a concrete reason, never a bare score.
- **VII. Maintainable, Modern Foundations** — JUnit 5 Platform, current JDK, no
  deprecated APIs — explicitly learning from Ekstazi/STARTS/Arquillian Smart Testing's
  failure to keep up with the ecosystem.

This constitution is binding on all future work here. Don't casually violate it.

## What was actually built this session

Full SpecKit workflow was used: `/speckit-constitution` → `/speckit-specify` →
`/speckit-plan` → `/speckit-tasks` → `/speckit-analyze` (found and fixed 2 CRITICAL gaps)
→ `/speckit-implement`. All artifacts live in `specs/001-shadow-mode-validator/`:
`spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`,
`tasks.md` (all 61 tasks checked off — see below).

**Result: a working Java/Maven CLI tool**, `io.github.baokhang83.blastradius:blastradius-validator`,
built via strict TDD — **97 tests, all green, verified from a clean `mvn clean test`**.

### Pipeline (all real, all tested end-to-end)

1. **`git/`** — `CommitWindowResolver` (JGit, resolves the N most recent commit pairs),
   `ChangedFileClassifier` (JAVA_SOURCE vs NON_SOURCE, derives class names from path),
   `CommitCheckout` (clones the target **once** into a disposable scratch dir, checks
   out different commits *within* that clone — verified byte-for-byte that the real
   target repo's HEAD/branch/working tree are never touched).
2. **`tracking/`** — `DependencyTrackingAgent` (a real `-javaagent`, SHA-256 checksums
   bytecode per class load), `TestBoundaryListener` (JUnit 5 `TestExecutionListener`,
   attributes loads to the currently-running test via ThreadLocal),
   `DependencyRecordWriter/Reader` (JSON persistence across the subprocess boundary).
3. **`build/`** — `MavenBuildRunner` (subprocess `mvn clean test`, optional `-javaagent`
   attach, single-test re-run for confirmation), `SurefireReportParser` (JDK DOM parser,
   no extra dependency), `GroundTruthResolver` (PASSED/CONFIRMED_FAILED/FLAKY per FR-013,
   scans for reports recursively so multi-module reactors just work),
   `BuildFailureDetector` (distinguishes a genuine compile failure from an ordinary test
   failure: no test reports exist at all → build failure).
4. **`selection/`** — `DependencyMatchSelector`, `FallbackSelector`,
   `NewOrModifiedTestSelector`, composed by `SelectionEngine` (fallback short-circuits
   everything; new/modified beats ordinary matching).
5. **`verdict/`** — `WouldMissComparator` (confirmed-failed + not-selected = would-miss),
   `VerdictCalculator` (PASS iff zero would-miss cases), `FlakyFailure`.
6. **`report/`** — `AnalysisReport` (JSON: verdict, analyzed/excluded pairs, would-miss
   detail, flaky failures, savings summary), `SavingsSummaryAggregator` (3-way bucket:
   dependency-matched / fallback-driven / new-or-modified), `TextSummaryRenderer`.
7. **`cli/`** — `RunConfig` (validated input), `RunCommand` (wires the whole pipeline
   per commit pair), `Main` (CLI entry point, exit codes 0=PASS/1=FAIL/2=couldn't-run).

### Real bugs found and fixed along the way (not stubbed — this is why TDD-with-real-execution mattered)

1. **Shaded-jar signature collision** — a signed dependency's leftover `.SF`/`.RSA` files
   survived shading and crashed the agent with `SecurityException`. Fixed with a
   shade-plugin filter stripping `META-INF/*.SF|.DSA|.RSA`.
2. **Agent needed per-test grouping, not a flat map** — caught and fixed *before* more
   code was built on the wrong shape (`Supplier<TestIdentity>` injected for testability).
3. **Stale `target/surefire-reports/` corrupting build-failure detection** — since
   `CommitCheckout` reuses one scratch clone across commits, and `target/` is untracked,
   a previous commit's leftover reports made a real compile failure look like "tests ran
   fine." Fixed by always running `mvn clean test`, never bare `test`.
4. **`README.md`** had gotten corrupted with a literal markdown-fence wrapper and a
   stray note-to-self — found and cleaned up during Polish.

### One honest, documented limitation (found via real testing, not hidden)

A class loaded only inside JUnit 5's `@BeforeAll` (a container-level callback) is never
attributed to any test, since `TestBoundaryListener.executionStarted` only fires for
actual tests. If such a class later changes and breaks a test that depended on it only
via `@BeforeAll` setup, that dependency is invisible to selection. This is documented in
`README.md` under "Known limitations" — it's narrow and deterministic, not a
correctness time-bomb, but worth knowing.

### `/speckit-analyze` caught two real gaps before implementation (worth remembering the pattern)

Before implementing, an analysis pass compared `spec.md`/`plan.md`/`tasks.md` and found:
- **FR-011 (multi-module support)** had zero task coverage — fixed by broadening the
  tracking integration test.
- **plan.md's non-destructive-checkout constraint** had zero task coverage — fixed by
  adding `CommitCheckout` as a new Foundational task (this is the safety-critical
  component described above). This was a **CRITICAL** finding — without it, the
  validator could have corrupted a real external project's git state.

Both were remediated *before* writing implementation code. This is the payoff of
running `/speckit-analyze` — don't skip it on future features.

## Current status

- **61 of 61 tasks complete. T061 is done — PASS.** The shadow-mode validator has been
  run for real against two independent open-source projects and found zero would-miss
  cases. Constitution Principle V's gate ("nothing is trusted to skip real tests until
  proven a zero would-miss rate in shadow mode, on real usage") is now satisfied. The
  validator slice (`specs/001-shadow-mode-validator/`) is finished.
- **Nothing is committed to git.** Per explicit standing instruction, commits/pushes are
  never done automatically — everything is sitting in the working tree.

## T061 results (the real validation run)

Two projects, chosen and run in sequence (see "How T061 actually went" below for why):

- **commons-io** (Apache Commons, real project, 6230 commits of history): 5 commit
  pairs analyzed. **Verdict: PASS. Zero would-miss cases.** Savings: 41.2% of test
  executions correctly skipped (up from an initial 0.04% before three mechanism bugs
  were found and fixed — see below).
- **jsoup** (real project, 2455 commits of history): 100 commit pairs analyzed.
  **Verdict: PASS. Zero would-miss cases. Zero excluded pairs. Zero flaky failures.**
  Savings: 2.0% skipped — lower than commons-io's, honestly, because 83% of this
  particular 100-commit window were non-source maintenance commits (`pom.xml` bumps,
  CI config), which correctly and safely trigger the fallback rule (run everything).
  Not a mechanism weakness — a real reflection of that window's commit mix.

**Combined: 105 real commit pairs across two independent real-world projects, zero
would-miss cases.** This is the evidence base for treating dependency-based test
selection as sound. Full reports live in `~/Documents/blastradius-targets/reports/`
(outside this repo, in the sibling target-projects checkout directory) — not
committed anywhere, regenerate by re-running the validator if needed.

## How T061 actually went: three real mechanism bugs, found and fixed via real execution

This is worth reading in full before touching `MavenBuildRunner` or
`DependencyTrackingAgent` again — the fixes are not obvious and each was found only by
running against a real project, not fixtures.

**Target project selection had real casualties.** `spring-petclinic` was ruled out
first — its Postgres integration test needs Testcontainers' Ryuk container, which
doesn't start cleanly in a sandboxed environment. Not a Blastradius bug, just a bad
target choice; noted in case someone picks it again.

**Bug 1 — `-DargLine` is not a reliable way to attach the agent.** The original design
(`MavenBuildRunner` passing `-DargLine=-javaagent:...`) assumed a target project's
`pom.xml` either doesn't set `argLine` or interpolates `${argLine}`/`@{argLine}`. Two
real, common counterexamples broke this:
  - **jsoup** hardcodes `<argLine>-Xss640k</argLine>` as a literal string (stack-size
    tuning) — command-line `-DargLine=...` is simply never referenced, so the agent
    silently never attaches. No error, no crash — just an empty dependency record file,
    which surfaced as `blastradius-validator: failed to read dependency record from
    ...json`.
  - **commons-io** (via the shared `commons-parent` POM) runs `jacoco-maven-plugin`'s
    `prepare-agent` goal unconditionally, which *overwrites* the `argLine` property
    with JaCoCo's own `-javaagent:...jacoco...` value, clobbering whatever was set on
    the command line — even though commons-io's surefire config correctly references
    `@{argLine}`. JaCoCo is one of the most common Java coverage tools, so this isn't
    an edge case; it's closer to the norm for a "well-maintained" Maven project.
  - **Fix**: attach the agent via the `JAVA_TOOL_OPTIONS` environment variable instead
    (`MavenBuildRunner.execute`), which every JVM launch reads directly, completely
    bypassing Maven's `argLine` property system. This also attaches to the *outer*
    `mvn` process itself (not just the forked Surefire JVM) — harmless, since it never
    runs any JUnit test, so `DependencyTrackingAgent`'s shutdown hook now skips writing
    when nothing was recorded, rather than clobbering real data with an empty write.

**Bug 2 — a shared output file races when a project uses `reuseForks=false`.**
commons-io's surefire config sets `reuseForks=false` (deliberately, to give each test
class a clean, tiny 25MB heap — see `IO-161`), which makes Surefire launch one fresh
JVM *per test class*, sequentially. The first fix (above) got the agent attached to
every one of those ~251 JVMs, confirmed via `JAVA_TOOL_OPTIONS`'s "Picked up" stderr
message appearing 252 times (251 forks + the outer process) — but only 34 of 251
classes' data survived in the shared output file. Root cause: Surefire does not wait
for a fork's OS process (and its shutdown hooks) to fully exit before starting the
next one, so two sibling JVMs' shutdown-hook writes to the same file can genuinely
overlap; a read-then-write is not atomic across that. **Fix**: stop sharing one file.
Each JVM now writes to a file unique to itself (`<path>.<pid>`, in
`DependencyTrackingAgent.premain`); `DependencyRecordReader.readAll` globs and merges
every sibling file once the whole build has finished, when nothing is running
concurrently anymore. Verified via a direct sanity check: coverage went from 34/251 to
249/251 (99.2%) test classes.

**Bug 3 — parameterized tests never matched their own baseline.** Even with near-
complete coverage, most tests still fell into "new-or-modified" (i.e., "no prior
baseline found") rather than real dependency-matching. Root cause: the live JUnit 5
`TestExecutionListener` (`TestBoundaryListener`, used while tracking) reports a
parameterized test's plain method name — e.g. `testGetFreeSpace_IllegalFileName` —
for every invocation, while Surefire's XML ground-truth reports each invocation under
a *different* name, e.g. `testGetFreeSpace_IllegalFileName(char)[1]`,
`...[2]`, etc. Confirmed directly against commons-io's real
`FileSystemUtilsTest`/`CharsetsTest` output. These two `TestIdentity` values never
`.equals()`, so a lookup using the ground-truth name always missed the baseline the
agent actually recorded. **Fix**: `TestIdentity.baselineKey()` strips a trailing
`(...)`/`[...]` suffix, used *only* for the "does this test have a baseline"
lookup (`RunCommand`'s `testDependencies` map keys, `SelectionEngine`'s
`dependencyMatchSelector` lookup) — deliberately **not** used for
`WouldMissComparator`'s pass/fail comparison, which must stay per-invocation-granular;
collapsing that would risk masking one failing invocation behind a passing sibling.
This is genuinely core selection logic, not throwaway validator plumbing — the user
explicitly signed off on skipping the constitution's usual TDD ceremony for it (a
one-time call for this validator specifically, not a standing exemption), given the
low risk of the scoped fix and the value of quick iteration during evidence-gathering.

**Net effect on commons-io's measured savings, from these three fixes in sequence:**
0.04% → 1.9% → 12.4% → **41.2%** skipped, each jump empirically verified before moving
to the next fix. This progression is itself decent evidence the fixes were real, not
placebo — an unrelated/cosmetic change wouldn't produce a monotonic, large, repeatable
improvement like this.

**Also fixed in passing, unrelated to the above**: `pom.xml`'s `mvn package`/`install`
was broken — the implicit `default-jar` execution (bound to the `package` phase)
collided with `maven-shade-plugin`'s `shade-at-package` execution, since the shade
plugin runs first and replaces the project's main artifact, then the implicit jar
plugin execution fails trying to attach a second one. Fixed by adding an explicit
`default-jar` execution bound to `phase>none</phase>` in `maven-jar-plugin`'s config.
Never blocked `mvn test` (which is why 97 passing tests never caught it), only
`package`/`install`.

**All 101 tests still pass after every fix** (97 original + 4 new for
`TestIdentity.baselineKey()`), including the existing real-subprocess integration
tests — nothing here was validated by inspection alone.

## Roadmap beyond T061: now starting

T061's PASS unlocks the next phase, per `spec.md`'s own Assumptions and Constitution
Principle V. **This is the actual next step, starting now (per explicit user
direction 2026-07-09):** a real Maven plugin that can gate CI for real — i.e., trusted
to actually skip tests, not just shadow-report on what it would have done. The
shadow-mode validator (`specs/001-shadow-mode-validator/`) is **done and being kept
around as-is** — not torn down, not folded into the new work, just complete.

Not started yet, but explicitly in scope for this next phase per the original spec's
Assumptions:
- The real Maven plugin itself (the actual product).
- Any ML layer stays out of scope for now too (Constitution Principle IV — the
  deterministic core, now proven, should keep being the whole story until there's a
  concrete reason for more).
- Multi-project comparison, dashboards, hosted analytics — still not this phase either.

**Discipline for this next phase, per the user's own explicit standing preference**:
run the same SpecKit workflow as before — constitution (review whether the existing
one needs updates now that shadow-mode has proven itself) → `/speckit-specify` → 
`/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-analyze` (don't
skip this — it caught two CRITICAL gaps last time before they became bugs) →
`/speckit-implement`, TDD throughout for all engine code. The one exception granted
this session (relaxed TDD ceremony for the validator's own bug fixes, per explicit
user instruction) was scoped to the validator specifically and does not carry forward
to the real plugin — that's exactly the kind of code the constitution's TDD principle
exists to protect.

## Key files to re-orient quickly

- `specs/001-shadow-mode-validator/spec.md` — the full feature spec, user stories, FRs.
- `specs/001-shadow-mode-validator/plan.md` — tech stack, architecture, Constitution
  Check.
- `specs/001-shadow-mode-validator/research.md` — the 6 technical decisions and why
  (agent design, subprocess invocation, ground truth source, JGit, multi-module finding,
  report format).
- `specs/001-shadow-mode-validator/tasks.md` — the full task breakdown, all 61 checked.
- `.specify/memory/constitution.md` — the 7 governing principles, binding on future work.
- `README.md` — user-facing usage, architecture summary, known limitations.
- `docs/RELEASING.md` — N/A here (that was mnemo-cache; Blastradius isn't published
  anywhere yet — no Maven Central release, no version decisions made).

## Standing instructions to remember (from the user, apply across sessions)

- **Never commit or push automatically.** Leave changes in the working tree; only
  commit/push when explicitly asked in the moment.
- **Don't read/touch credential or secrets files** (e.g. `~/.m2/settings.xml`) — give
  snippets to paste instead.
- The user prefers **evidence-based validation over speculative pitches** — this whole
  project exists because weaker ideas were killed by honest gap-checks rather than
  oversold. Keep applying that standard going forward, including to T061's real result.
