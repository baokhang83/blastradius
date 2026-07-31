# Session: Compile the injected tracking agent for Java 17 while retaining the validator's Java 21 baseline

- **intent:** Compile the injected tracking agent for Java 17 while retaining the validator's Java 21 baseline
- **started:** 2026-07-31

## Knowledge transfer

- **The Java boundary is the injected agent, not the validator process.** The validator CLI
  remains a Java-21 artifact, but Maven target JVMs load `DependencyTrackingAgent` through
  `-javaagent`. A target that runs Java 17 can load class-file version 61 and rejects version 65
  before any test begins. · status: documented
- **Core production and test sources have different compatibility needs.** Core production code
  crosses into target JVMs and therefore compiles with release 17. Its own tests still compile
  with release 21, preserving existing use of Java-21 test conveniences such as `List.getFirst()`
  without expanding the agent's runtime requirement. · status: documented
- **The compatibility regression is bytecode-level and directly testable.**
  `Java17AgentCompatibilityTest` reads the agent entry point's class-file header and requires
  major version 61. A real Java-17 `-javaagent` launch of the shaded validator jar additionally
  verifies that loading the entry point and its startup path succeeds. · status: documented

## Decision: compile the tracking core for Java 17 while retaining Java 21 validator and test baselines

- **where:** `blastradius-core/pom.xml compiler configuration and tracking-agent packaging`
- **why:** the tracking agent is injected into the target build JVM, so Java 17 targets reject a Java 21 agent before tests begin; compiling core production classes to release 17 makes the existing agent entry point loadable while the validator CLI and build enforcer remain Java 21 or later
- **alternative:** lower the entire Blastradius reactor to Java 17 — rejected: it needlessly weakens the validator's established Java 21 baseline and forces unrelated code and tests to give up Java 21 features
- **design:** ../design.md#approach
- **constitution:** §I, §III, §VI
- **trust:** ✓ verified
