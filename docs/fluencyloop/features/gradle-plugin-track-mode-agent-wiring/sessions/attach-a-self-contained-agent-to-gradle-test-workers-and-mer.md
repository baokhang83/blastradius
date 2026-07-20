# Session: attach a self-contained agent to Gradle Test workers and merge TRACK records

- **intent:** attach a self-contained agent to Gradle Test workers and merge TRACK records
- **started:** 2026-07-20

## Knowledge transfer

- `blastradius-core` exposes `agentElements`, whose `agentJar` is an embedded-dependency JAR
  with the agent manifest. The Gradle plugin consumes that exact variant, so the loaded
  `DependencyTrackingAgent` has a real JAR code source that can be passed to `-javaagent`.
- `GradleTrackAction.prepare` only acts when `HEAD` equals `baseRef`; it adds the JVM argument
  before Gradle executes `Test`. On every other commit, `GradleSelectAction` retains the
  established SELECT or safe full-suite fallback behavior.
- The core agent writes one `<prefix>.<pid>` JSON record for each worker JVM. `complete` runs
  after the task, merges those records through the existing `DependencyRecordReader`, and writes
  the shared index with the baseline commit as its anchor. A missing record is a TRACK failure,
  never a partial index.
- TRACK currently relies on a normally executed `Test` task. Configuration-cache and up-to-date
  task modelling are deliberately deferred to issue #19.

## Decision: Package tracking as a self-contained core agent artifact

- **where:** `blastradius-core agentJar and GradleTrackAction`
- **why:** A Java agent receives only its own JAR, so the agent must embed its runtime dependencies before Test.doFirst attaches it; Test.doLast can then safely merge each worker's shutdown record.
- **alternative:** Attach the ordinary core JAR — rejected because the agent's JSON writer would lack its runtime dependencies.
- **design:** ../design.md#boundaries-and-decisions
- **constitution:** §III, §VI
- **trust:** ✓ verified
