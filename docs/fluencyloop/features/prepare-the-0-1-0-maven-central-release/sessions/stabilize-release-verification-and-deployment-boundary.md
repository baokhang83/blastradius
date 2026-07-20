# Session: stabilize release verification and deployment boundary

- **intent:** stabilize release verification and deployment boundary
- **started:** 2026-07-20

## Knowledge transfer

- **Central publishing lifecycle:** The Central extension gathers attached artifacts from each
  reactor project on `deploy`; `maven.deploy.skip` does not limit that gathering. The release
  profile must therefore exclude internal artifact IDs directly in the Central configuration.
  **Status:** documented.
- **SELECT Invoker fixture:** The hook commits its tracked baseline before it creates `NewTest`,
  then asserts that `HEAD` and `baseline` remain distinct. This avoids dependence on ignored,
  unversioned fixture files being copied into a clean Invoker project. **Status:** documented.

## Decision: filter Central artifacts at the publishing boundary

- **where:** `pom.xml release profile`
- **why:** The Central lifecycle extension collects reactor artifacts independently of Maven deploy-skip, so an explicit artifactId filter is required to keep internal modules out of the public bundle.
- **alternative:** Rely on maven.deploy.skip alone — rejected: it does not constrain Central's collector and staged the parent and core.
- **design:** ../design.md
- **constitution:** §IV
- **trust:** ✓ verified

## Decision: generate the SELECT fixture's new test in the prebuild hook

- **where:** `blastradius-maven-plugin/src/it/select/prebuild.groovy`
- **why:** Creating NewTest after the tracked baseline makes the fixture independent of Invoker project-copy behavior and asserts that baseline and HEAD differ before the SELECT run.
- **alternative:** Rename an ignored template file — rejected: the file is not versioned and is absent from a clean checkout.
- **design:** ../design.md
- **constitution:** §I
- **trust:** ✓ verified
