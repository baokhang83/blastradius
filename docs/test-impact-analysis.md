# Open-source JVM test impact analysis

Test impact analysis, also called regression test selection, identifies tests to run for a
change using dependency or coverage information. Blastradius records observed class loads per
test on a base branch, compares changed Java and conventional Kotlin source files against a
Git merge base, and applies selection and conservative fallback rules. It supports Maven and
has a Gradle plugin on the Gradle Plugin Portal. See the [Maven guide](maven-junit5-test-impact-analysis.md)
and [Gradle guide](gradle-test-impact-analysis.md) for availability and setup.

## Compare approaches

The following is a summary of the projects' own documentation, checked 2026-11-06. It is not
a benchmark or a guarantee of compatibility. Check each project's current documentation and
validate the exact build and test framework combination you use.

| Project | Selection data | Build and test integrations | State and setup |
| --- | --- | --- | --- |
| [Blastradius](../README.md) | Observed per-test class dependencies, plus conservative static-invocation fallback. | Published Maven plugin for JUnit 5, and a Gradle plugin on the Gradle Plugin Portal. | Saves a commit-keyed index locally; CI can cache it or use S3-compatible storage. |
| [Tia](https://github.com/mtgleeson/tiatesting) | JaCoCo coverage mapped to source changes. | Documents Maven and Gradle integrations for JUnit 5/6; Gradle also supports Spock. | Uses a database for mappings and documents JaCoCo TCP collection, including for separately running code. |
| [SAP Smart Test Picker](https://github.com/SAP/smart-test-picker) | Per-test JaCoCo coverage matched to changed methods, with class-level fallback. | Documents Gradle and Maven plugins and JUnit 5 collection, plus a CLI. | Uses a coverage map; documents full-suite fallback for missing or stale maps. |

These tools make different tradeoffs in coverage collection, integration, and selection
granularity. A baseline map can become stale or incomplete, and runtime instrumentation
cannot guarantee every dependency is observed. Evaluate accuracy on your own suite and keep
scheduled full-suite runs.

## Blastradius validation

The [historical replay analysis](../HISTORICAL_REPLAY_ANALYSIS.md) describes four projects,
200 consecutive commit pairs per project, exclusions, and bounded mutation experiments. The
README reports 61.4% fewer selected test executions in aggregate; that is not a measurement
of CI wall-clock savings. In the commons-io mutation experiment, 1,011 of 31,648 killing
test occurrences were not selected, although at least one killing test was selected for each
caught mutant. Results are specific to those experiments and do not prove universal safety.
