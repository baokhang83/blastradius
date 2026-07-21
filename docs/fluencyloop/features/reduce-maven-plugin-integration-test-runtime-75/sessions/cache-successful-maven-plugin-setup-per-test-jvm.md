# Session: Cache successful Maven plugin setup per test JVM

- **intent:** Cache successful Maven plugin setup per test JVM
- **started:** 2026-07-21

## Decision: Cache plugin setup only after success

- **where:** `blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/PluginInstaller.java; blastradius-maven-plugin/src/test/java/io/github/baokhang83/blastradius/plugin/mojo/EndToEndTestSupport.java`
- **why:** A single synchronized test-JVM guard removes repeated identical Maven installs while caching only a successful process result, so a failed setup remains retryable and each scenario still runs its own real fixture build.
- **alternative:** Reuse fixture directories, remove clean, or persist a cross-run cache — rejected because those approaches can leak state or hide isolation failures.
- **design:** ../design.md#class-diagram
- **constitution:** §I, §II, §III
- **trust:** ✓ verified

## Knowledge transfer

- **PluginInstaller:** keeps a synchronized, in-memory success flag for the Surefire JVM. It
  invokes the supplied Maven-install action exactly once after it succeeds; an exception leaves
  the flag unset, allowing a later request to retry. **Status:** verified by focused unit tests.
- **EndToEndTestSupport:** delegates its existing plugin-install command to `PluginInstaller`,
  while `trackDependencies` and `runMvnTest` continue launching separate `mvn clean test`
  processes in fresh fixture directories. **Status:** verified by the full Maven-plugin suite.
