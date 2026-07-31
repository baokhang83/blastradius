# Session: Keep JUnit Platform out of the shaded tracking agent, while retaining JUnit-free premain startup for Java 17 target builds.

- **intent:** Keep JUnit Platform out of the shaded tracking agent, while retaining JUnit-free premain startup for Java 17 target builds.
- **started:** 2026-07-31

## Decision: Keep JUnit Platform out of the shaded tracking agent

- **where:** `blastradius-core/pom.xml, blastradius-validator/pom.xml, tracking startup`
- **why:** The target Surefire/Failsafe fork owns the JUnit Platform SPI; bundling Blastradius's compile-time copy into the agent can shadow a target's newer Platform classes. A JUnit-free context lets the agent start in Maven's outer JVM, which has no JUnit runtime.
- **alternative:** Bundle a fixed JUnit Platform version — rejected: it can shadow or mix with the target runtime and makes cross-version JUnit 5 interoperability unsafe.
- **design:** ../design.md#junit-platform-classpath-ownership
- **constitution:** §III, §VI
- **trust:** ✓ verified
