# Session: validator foundations: build invocation, ground truth, git replay, report

- **intent:** validator foundations: build invocation, ground truth, git replay, report
- **started:** 2026-07-12

> ✓ **Backfilled from a real-time record, then reviewed.** `blastradius-validator` was built via
> the full SpecKit workflow *with* a contemporaneous development log
> (`.claude/worktrees/…/SESSION.md`) — real-time teaching **did** happen; it just wasn't in
> FluencyLoop's journal format. This session reconstructs that journal from
> `specs/001-shadow-mode-validator/` + `SESSION.md` + the code, then **confirmed by the maintainer
> on 2026-07-12** — all five decisions upgraded from `⚠` to `✓`. Because the rationale is backed by
> a contemporaneous log (not post-hoc memory), these entries carry more weight than a typical
> blind backfill. Principle numbers reference the current constitution v2.1.0 — the SESSION.md
> predates it (its Principle VII "Maintainable, Modern Foundations" is now §VI; its Principle V
> "Shadow-Mode Before Gating" was removed in v2.0.0).

---

## Knowledge transfer

_The ground this backfill makes understandable — the validator's components, roles, conditions,
and the hard-won mechanism lessons from the real T061 run. About the work, not any person._

**What it is, in one line:** a *shadow-mode* validator — it always runs the full suite, then
checks afterward whether dependency-based selection *would have* missed a real failure (must be
zero) and how much it would have saved. It never skips a real test itself.

### The pipeline (role · conditions)

- **`cli/` — `Main` + `RunConfig` + `RunCommand`** — CLI entry with validated input; `RunCommand`
  wires the whole pipeline per commit pair. Exit codes: `0` PASS, `1` FAIL (a would-miss), `2`
  couldn't-run. · status: documented
- **`git/` — `CommitWindowResolver` + `CommitCheckout` + `CommitPair`** — JGit resolves the N most
  recent commit pairs, then clones the target **once** into a disposable scratch dir and checks out
  each commit *within* that clone — the real target repo's HEAD/branch/worktree are never touched
  (safety-critical; a `/speckit-analyze` CRITICAL gap that was fixed before implementation). · status: documented
- **`tracking/` — `DependencyTrackingAgent` + `TestBoundaryListener` + `DependencyRecordWriter`/`Reader`**
  (from core) — a real `-javaagent` SHA-256-checksums each class as it loads and attributes it to
  the running test via a `ThreadLocal`; records persist as JSON across the subprocess boundary. · status: documented
- **`build/` — `MavenBuildRunner` + `SurefireReportParser` + `GroundTruthResolver` + `BuildFailureDetector`**
  — runs the target's own `mvn clean test` as a subprocess (agent via `JAVA_TOOL_OPTIONS`);
  `clean` is mandatory because one scratch clone is reused across commits and `target/` is
  untracked. Ground truth is parsed from Surefire/Failsafe XML with the JDK DOM parser (no extra
  dep), classified `PASSED`/`CONFIRMED_FAILED`/`FLAKY` (failures re-run once, FR-013), scanning
  report dirs recursively so multi-module reactors just work. **No reports at all ⇒ a build/compile
  failure, distinct from a test failure** (`BuildFailureDetector`). · status: documented
- **`selection/` — `SelectionEngine` + the three selectors** (from core) — fallback short-circuits
  everything; new/modified beats ordinary dependency matching. · status: documented
- **`verdict/` — `WouldMissComparator` + `VerdictCalculator` + `FlakyFailure`** — a test that was
  `CONFIRMED_FAILED` **and** not selected is a would-miss; the run is PASS iff there are zero. · status: documented
- **`report/` — `AnalysisReport` + `SavingsSummaryAggregator` + `ReportWriter` + `TextSummaryRenderer`**
  — one indented-JSON file is the source of truth (FR-010); savings are bucketed three ways
  (dependency-matched / fallback-driven / new-or-modified); the text summary is a rendering, never
  a second source. · status: documented

### Hard-won mechanism conditions (found only by running against real projects — worth keeping)

- **Agent attach must use `JAVA_TOOL_OPTIONS`, not `-DargLine`** — real poms defeat `argLine`:
  jsoup hardcodes a literal `<argLine>`, and JaCoCo's `prepare-agent` overwrites the property
  (commons-parent). `-DargLine` failed *silently* (empty record, not an error). See D2. · status: documented
- **Per-PID dependency file, merged on read** — with `reuseForks=false` (commons-io) Surefire
  launches one JVM per test class and doesn't wait for a fork to fully exit, so a shared output
  file races (only 34/251 classes survived). Each JVM writes `<path>.<pid>`, merged after the build
  (→ 249/251). See D2. · status: documented
- **`TestIdentity.baselineKey()` strips a trailing `(…)`/`[…]` suffix** — the live JUnit listener
  reports a parameterized test's plain method name, but Surefire XML reports each invocation under a
  distinct name (`…(char)[1]`), so the two `TestIdentity` values never `.equals()` and every lookup
  missed its baseline. The stripped key is used **only** for the baseline lookup — deliberately
  **not** for the would-miss comparison, which must stay per-invocation so a failing invocation
  isn't masked behind a passing sibling. (Core selection logic — arguably its own decision.) · status: follow-up
- **`@BeforeAll`-only class loads are unattributed** — a class loaded solely inside a container-level
  `@BeforeAll` is never tied to a test, so a later change to it is invisible to selection. Narrow,
  deterministic, documented in README "Known limitations" — not a correctness time-bomb. · status: documented
- **Shaded-jar signature stripping** — a signed dependency's leftover `META-INF/*.SF|.DSA|.RSA`
  survived shading and crashed the agent with `SecurityException`; the shade plugin filters them. · status: documented

---

## Decision: invoke the target's own `mvn test` as an unmodified subprocess for ground truth

- **where:** `blastradius-validator/.../build/MavenBuildRunner.java`
- **why:** the validator's whole value is fidelity to the target's *real* build, so it shells out
  to the project's own `mvn -B clean test` and reads what that build produces — never altering the
  target or second-guessing its configuration (parallelism, forks, includes/excludes). (research.md §2, plan.md non-destructive constraint)
- **alternative:** fork/patch the target `pom.xml` to add a plugin (mutates the artifact under
  test, diverging from its real behaviour); or reimplement execution via the JUnit Platform
  Launcher (would have to reconstruct the project's classpath + Surefire config, which risks not
  matching the trusted CI behaviour). Both rejected.
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-12)

---

## Decision: attach the tracking agent via `JAVA_TOOL_OPTIONS` (revised from `-DargLine` during T061)

- **where:** `blastradius-validator/.../build/MavenBuildRunner.java` (agent-attach in `execute`)
- **why:** `JAVA_TOOL_OPTIONS` is read directly by *every* JVM launch (the outer `mvn` and each
  forked Surefire/Failsafe test JVM), independent of Maven's `argLine` property. The original
  `-DargLine=-javaagent:...` attach **failed silently** on real projects during T061 validation —
  jsoup hardcodes a literal `<argLine>` (never interpolated from a CLI property), and JaCoCo's
  `prepare-agent` goal *overwrites* the `argLine` property after our override (hit via
  commons-parent). A related fix: with `reuseForks=false` (commons-io) each test class gets a fresh
  JVM and a single shared output file races, so each JVM writes to a `.<pid>` file, merged on read
  once the build is done. (research.md §2)
- **alternative:** keep `-DargLine=...` — rejected: silently defeated by the target's own
  `argLine` handling, surfacing as empty records rather than an error. *(Strong T061 evidence —
  confirmed ✓ on review.)*
- **note:** the class-level Javadoc still describes the old `-DargLine` approach; the code was
  revised to `JAVA_TOOL_OPTIONS` but that doc comment wasn't updated (stale — worth a follow-up).
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-12)

---

## Decision: ground truth from the standard Surefire/Failsafe XML reports

- **where:** `blastradius-validator/.../build/SurefireReportParser.java`, `build/GroundTruthResolver.java`
- **why:** the `TEST-*.xml` reports are the authoritative, standard output of the exact build just
  run — reusing them avoids re-deriving results through a second mechanism that could disagree with
  the real build, and the format is stable and well-documented. (research.md §3)
- **alternative:** a custom JUnit Platform result listener reporting outcomes directly — rejected as
  redundant: since the build already runs as a subprocess, the XML is the natural zero-integration
  output; a listener would be a second, potentially-disagreeing source.
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-12)

---

## Decision: JGit in-process for commit-window traversal and non-destructive checkout

- **where:** `blastradius-validator/.../git/CommitCheckout.java`, `git/CommitWindowResolver.java`
- **why:** JGit walks the commit range and materializes each commit into a disposable scratch clone
  in-process — testable (no subprocess/output-parsing brittleness), actively maintained, and it
  never touches the target repo's HEAD/branch/worktree. (research.md §4, plan.md non-destructive)
- **alternative:** shell out to the `git` CLI via `ProcessBuilder` — rejected: harder to unit test
  (needs a real git binary + process mocking), output parsing is brittle across git versions, and
  offers no advantage for these operations.
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-12)

---

## Decision: JSON report as the single source of truth + a rendered text summary, no database

- **where:** `blastradius-validator/.../report/AnalysisReport.java`, `report/ReportWriter.java`, `report/TextSummaryRenderer.java`
- **why:** each run emits one indented-JSON file as the machine-auditable record (FR-010), and the
  plain-text summary is a *rendering* of it — not a second data source. JSON is dependency-light and
  satisfies "reproducible from recorded output alone" without a persistence layer the spec puts out
  of scope. (research.md §6)
- **alternative:** a relational/embedded database (e.g. SQLite) for run history — rejected as
  unnecessary infrastructure for a tool that analyses one project per run and needs no cross-run
  querying.
- **constitution:** §II (Clean Code & Simplicity — no speculative infrastructure for capability not yet needed).
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-12)
