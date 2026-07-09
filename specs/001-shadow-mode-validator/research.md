# Phase 0 Research: Shadow-Mode Test Selection Validator

## 1. Dynamic per-test class-dependency tracking mechanism

**Decision**: A custom, lightweight `java.lang.instrument` agent (`-javaagent`) using a
`ClassFileTransformer` to observe class loads, combined with a JUnit 5 `TestExecutionListener`
that marks the "currently executing test" (via a `ThreadLocal`) so each loaded class can be
attributed to the test that triggered it. For each loaded class, the agent records the class
name and a checksum of its bytecode (e.g., SHA-256 of the raw `.class` bytes as seen by the
transformer).

**Rationale**: We need "which classes did this test touch" plus "did that class's bytecode
change," not line-level coverage. A purpose-built load-tracker is simpler to reason about,
test, and keep dependency-free than embedding a full coverage engine, and this is the same
fundamental technique dynamic RTS tools (Ekstazi) use — build a minimal dependency tracker
rather than a coverage tool. This keeps the agent small, auditable, and aligned with
Constitution Principle VII (no dependency on abandoned/heavyweight tooling) and Principle II
(no more machinery than the problem needs).

**Alternatives considered**:
- *Reuse JaCoCo's runtime agent* (`org.jacoco.agent`) and derive touched-classes from coverage
  data. Rejected: JaCoCo is built for line/branch coverage reporting, not incremental
  per-test dependency snapshots; embedding and resetting its `RuntimeData` between tests adds
  real complexity for capability we don't need (we only need "touched or not" + a checksum).
- *Static bytecode analysis only* (like STARTS). Rejected outright — this is exactly what the
  constitution (Principle VII, Technology Constraints) rules out: static analysis cannot see
  reflective, DI, or classpath-scanning edges that real Java/Spring-style applications rely on,
  which is precisely the soundness gap this validator exists to avoid.

## 2. Injecting the agent into the target project's own Maven build

**Decision (revised during T061's real-project validation — see below)**: Invoke the target
project's own `mvn test` / `mvn verify` as a subprocess, with the tracking agent attached via
the `JAVA_TOOL_OPTIONS` environment variable (`-javaagent:<path-to-agent.jar>=<output-path>`).
No modification of the target project's `pom.xml` or any committed file.

**Original decision (superseded)**: the initial implementation attached the agent via
`-DargLine=-javaagent:...` on the Maven command line, relying on Surefire's `argLine`
property. This worked against every fixture project used during TDD, but **T061's real-project
validation run (see SESSION.md) found it unreliable against real Maven projects**, for two
independent reasons:
- Some projects hardcode a literal `<argLine>` value in their surefire config (e.g. jsoup's
  `<argLine>-Xss640k</argLine>` for stack-size tuning) — a literal XML value is never
  interpolated from a command-line property, so `-DargLine=...` is silently never referenced.
- Some projects run `jacoco-maven-plugin`'s `prepare-agent` goal, which itself *sets* the
  `argLine` property (to attach JaCoCo's own coverage agent) — and does so *after* our
  command-line override, silently replacing it. This isn't a rare case: JaCoCo is one of the
  most common Java coverage tools, and commons-io hit exactly this (via the shared
  `commons-parent` POM) during T061.

Both failure modes are silent — no error, just an agent that never actually attaches, which
surfaced as an empty/missing dependency record rather than an exception pointing at the cause.
`JAVA_TOOL_OPTIONS` sidesteps the whole `argLine` property system: it's a standard JVM
environment variable read directly by *every* JVM launch, regardless of how (or whether) the
target's own `pom.xml` manages `argLine`. It also attaches to the outer `mvn` process itself,
not just the forked Surefire JVM(s) — harmless, since the outer process never runs any JUnit
test, so the agent's shutdown hook simply skips writing when nothing was recorded rather than
overwriting real data with an empty result.

**A second, related issue T061 found**: a target project configured with `reuseForks=false`
(commons-io, again for legitimate reasons — isolating each test class to a clean, tiny heap)
makes Surefire launch one fresh JVM *per test class*, sequentially. The agent correctly
attached to every one of those JVMs, but a naive single shared output file, written by each
JVM's shutdown hook, lost most of the data — Surefire does not wait for one fork's OS process
(and its shutdown hooks) to fully exit before starting the next, so two sibling JVMs' writes to
the same file can genuinely race. **Fix**: each JVM writes to a file unique to itself
(`<path>.<pid>`); the parent process merges every sibling file into one map only once the whole
build has finished, when nothing is running concurrently anymore.

**Rationale**: The validator must never alter the target repository (it's replaying real,
external project history) and must not reimplement or second-guess the target's own build —
that would undermine the "ground truth" the whole validator depends on. `JAVA_TOOL_OPTIONS` is
a standard, well-documented JVM mechanism for exactly this kind of non-invasive instrumentation
attachment, so no build-file changes or custom plugins are needed on the target project's side
— and unlike `argLine`, it doesn't depend on cooperation from (or absence of conflict with) the
target's own Maven plugin configuration.

**Alternatives considered**:
- *Fork the target repo and add a plugin declaration*. Rejected: mutates the artifact under
  test, risking divergence from the real project's actual build behavior and defeating the
  purpose of validating against unmodified history.
- *Reimplement test execution via the JUnit Platform Launcher API directly, bypassing Maven*.
  Rejected: would require independently reconstructing the target project's classpath, Surefire
  configuration (parallelism, includes/excludes, forked JVM settings), which risks producing
  results that don't match the project's actual, trusted CI behavior — undermining "ground
  truth."
- *`-DargLine=...`* — the original decision. Superseded per above; kept here rather than
  deleted, since the failure modes that ruled it out are exactly the kind of real-world
  evidence this project's whole methodology is built on surfacing honestly.

## 3. Obtaining ground-truth pass/fail results

**Decision**: Parse the standard Surefire (`target/surefire-reports/*.xml`) and Failsafe
(`target/failsafe-reports/*.xml`) XML report files that Maven already produces after a run.

**Rationale**: These are the authoritative, standard output of the exact build we just ran —
using them avoids re-deriving results through a second mechanism that could disagree with the
real build, and it's a stable, well-documented, actively-maintained format.

**Alternatives considered**:
- *Custom JUnit Platform `TestExecutionListener` reporting results directly*. Rejected as the
  primary source: since we're invoking Maven as a subprocess (decision #2), the XML reports are
  already the natural, zero-extra-integration output; a custom listener would be redundant and
  is only needed for the dependency-tracking attribution in #1, not for ground truth.

## 4. Git history traversal and per-commit-pair diffing

**Decision**: JGit (`org.eclipse.jgit`), used in-process to walk the commit range, obtain the
changed-files list per commit pair, and check out each commit into a working copy.

**Rationale**: In-process, testable (no subprocess/output-parsing brittleness), actively
maintained by the Eclipse Foundation, and the de facto standard Java git library — consistent
with Constitution Principle VII.

**Alternatives considered**:
- *Shell out to the `git` CLI via `ProcessBuilder`*. Rejected: harder to unit test (requires a
  real git binary and process mocking), output-format parsing is brittle across git versions,
  and offers no advantage over JGit for the operations this feature needs.

## 5. Multi-module reactor dependency attribution (FR-011)

**Finding**: This mostly falls out of the dynamic tracking approach (#1) for free. When Maven
builds a multi-module reactor (`mvn test` at the root), it builds modules in dependency order
and each module's tests run with already-built upstream modules on the classpath. Because the
tracking agent records *actual* class loads during test execution — not a per-module static
graph — a test in module B that transitively loads a class from module A is captured
automatically, regardless of module boundaries. No separate cross-module graph-construction
step is required; the module boundary is invisible to the dynamic tracker by construction.

**Remaining work**: The checksum/changed-class detection (comparing "did this class change
between commit pairs") must key classes by a stable identity (fully-qualified class name) that
doesn't depend on which module produced them, since a changed class must be matched by name
against what was recorded as a dependency regardless of which module's `target/classes` it
came from in a given build.

## 6. Report format

**Decision**: Each run emits a single structured JSON file (the authoritative, machine-
auditable record — satisfies SC-005/FR-010) plus a derived human-readable plain-text/Markdown
summary printed to stdout and optionally written alongside it. The JSON is the source of truth;
the text summary is a rendering of it, not a second data source.

**Rationale**: JSON is simple, dependency-light to produce (a well-maintained library, e.g.
Jackson, is sufficient — no schema-validation framework needed for a single-consumer internal
tool), and satisfies the "reproducible from recorded output alone" requirement (FR-010) without
needing a database or persistence layer, which the spec's Assumptions explicitly place out of
scope (standalone, offline tool).

**Alternatives considered**:
- *A relational/embedded database (e.g., SQLite) for run history*. Rejected as unnecessary for
  this slice: the spec analyzes one project per run and doesn't require cross-run querying;
  a single self-contained report file is simpler and satisfies every stated requirement
  (Principle II: no speculative infrastructure for capability not yet needed).

## Summary of chosen stack

| Concern | Choice |
|---|---|
| Language/runtime | Java 21 (LTS) |
| Build tool (of this project) | Maven |
| Dependency tracking | Custom `java.lang.instrument` agent + `ClassFileTransformer` |
| Test-boundary attribution | JUnit 5 `TestExecutionListener` + `ThreadLocal` |
| Ground truth | Surefire/Failsafe XML report parsing |
| Git access | JGit |
| Report format | JSON (source of truth) + rendered text summary |
| Target invocation | Subprocess `mvn` with agent attached via `JAVA_TOOL_OPTIONS` (revised from `-DargLine=...` during T061 — see §2) |
