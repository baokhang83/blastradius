# Session: validator foundations: build invocation, ground truth, git replay, report

- **intent:** validator foundations: build invocation, ground truth, git replay, report
- **started:** 2026-07-12

> ✓ **Backfilled, then reviewed.** `blastradius-validator` shipped from
> `specs/001-shadow-mode-validator/` without real-time teaching. These decisions were
> reconstructed from that spec/plan/`research.md`/contracts and the code, then **confirmed by the
> maintainer on 2026-07-12** — all five upgraded from `⚠` to `✓`. Principle numbers reference the
> current constitution v2.1.0.

---

## Knowledge transfer

_The ground this backfill makes understandable — the validator's components, roles, and
conditions. About the work, not any person._

- **RunCommand** — orchestrates one run: resolve the commit window, and for each pair check out,
  get ground truth, run selection, compute the verdict, and write the report. · status: documented
- **MavenBuildRunner** — invokes the target's own `mvn -B clean test` as a subprocess (never
  reimplementing the build); attaches the tracking agent through the `JAVA_TOOL_OPTIONS` env var;
  `clean` is required because one scratch copy is reused across commits and `target/` is untracked. · status: documented
- **GroundTruthResolver + SurefireReportParser** — run the full suite once, re-run each failure
  once to rule out flakiness (FR-013), and read pass/fail from the standard Surefire/Failsafe XML;
  scans report dirs anywhere under the root so multi-module reactors work. · status: documented
- **CommitCheckout** — clones the target once into a disposable scratch dir and checks out each
  commit there via JGit, never touching the target repo's HEAD/branch/worktree. · status: documented
- **VerdictCalculator + WouldMissCase** — compares the engine's selection against ground truth; a
  test the engine did *not* select that actually failed is a would-miss (the soundness signal). · status: documented
- **AnalysisReport / ReportWriter / TextSummaryRenderer** — the run's single JSON source of truth
  (FR-010); the text summary is a rendering of it, never a second data source. · status: documented

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
