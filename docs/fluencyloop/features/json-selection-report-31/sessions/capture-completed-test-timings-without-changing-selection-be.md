# Session: capture completed test timings without changing selection behavior

- **intent:** capture completed test timings without changing selection behavior
- **started:** 2026-07-26

## Knowledge transfer

- **TimingHistoryRecorder:** A session-level listener wrapper forwards all Maven events to the original listener. On Surefire/Failsafe completion, it reads the emitted JUnit XML and updates a project-local timing cache; cache errors only warn and never alter the build result.
- **TestTimingHistory:** The cache has its own format version and stores a stable test identity with its most recently observed duration in milliseconds. SELECT estimates savings only when every skipped test has a sample, while TRACK and FALLBACK report zero savings.

## Decision: chain Maven execution listeners to record completed test timings

- **where:** `blastradius-maven-plugin mojo/SelectMojo and report/TimingHistoryRecorder`
- **why:** The select goal runs before tests, so it wraps Maven's dynamic session listener and records standard Surefire/Failsafe XML only after the test engine completes, preserving existing listener behavior and keeping the timing cache observational.
- **alternative:** Require a second post-test goal — rejected: every adopter would need additional lifecycle configuration; inject a test-runtime listener — rejected: it adds execution risk without changing selection.
- **design:** ../design.md#sequence-write-one-report-for-every-selection-mode
- **constitution:** §II
- **trust:** ✓ verified
