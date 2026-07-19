# Session: implement Maven Central release pipeline

- **intent:** implement Maven Central release pipeline
- **started:** 2026-07-19

## Decision: Publish one self-contained plugin artifact

- **where:** `blastradius-maven-plugin/pom.xml`
- **why:** Publish only the Maven plugin, with core embedded and JGit/Jackson relocated, so consumer builds need no internal companion artifact and cannot collide with their dependency versions.
- **alternative:** Publish `blastradius-core` alongside the plugin — rejected: it exposes an implementation artifact and leaves consumers responsible for matching internal versions.
- **design:** `../design.md`
- **constitution:** §III, §VI
- **trust:** ✓ verified

## Decision: Flatten the consumer POM and deploy only the plugin

- **where:** `pom.xml and blastradius-maven-plugin/pom.xml`
- **why:** Keep the reactor modules deploy-skipped and flatten the plugin consumer POM so Maven Central receives one standalone, complete coordinate.
- **alternative:** Deploy the reactor parent and every module — rejected: Central consumers do not need the internal engine or validator artifacts.
- **design:** `../design.md`
- **constitution:** §IV, §VI
- **trust:** ✓ verified

## Decision: Test release behavior through isolated Maven projects

- **where:** `blastradius-maven-plugin/src/it`
- **why:** Exercise TRACK, SELECT, FALLBACK, and conflicting consumer dependency versions through Maven Invoker projects rather than only unit-level wiring tests.
- **alternative:** Cover release wiring only with unit tests — rejected: it cannot prove the packaged plugin resolves or operates correctly in a consumer Maven process.
- **design:** `../design.md`
- **constitution:** §I, §III, §IV
- **trust:** ✓ verified
