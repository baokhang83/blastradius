# Changelog

All notable changes to this project are documented in this file.

## [0.3.2] - 2026-08-01

### Documentation

- Clarified the scope of the validator's historical shadow-mode results.

### Known limitations

- Historical replay currently walks commits reachable from the checked-out `HEAD` and pairs
  adjacent entries in that traversal. On non-linear Git history, a reported pair is not
  necessarily a direct parent-to-child change. The current results must therefore not be read as
  an exact simulation of every individual target-branch change. See [#186](https://github.com/baokhang83/blastradius/issues/186).
- Merged history is usually green. A result of zero would-miss cases means that the validator
  observed no confirmed failing test that its selection would skip; it does **not** prove that
  selection catches regressions when the analyzed window contains few or no newly failing tests.
  Current reports also do not expose the denominator of confirmed failures that were correctly
  selected.
- Build failures before test reports exist are excluded from the validator verdict: Maven has
  already failed before test selection could affect the outcome.
- Mutation-based fault injection is planned to create controlled, test-killed regressions and
  produce meaningful positive soundness evidence. See [#187](https://github.com/baokhang83/blastradius/issues/187).

## [0.3.1] - 2026-07-27

### Added

- Aggregated Blastradius selection feedback across the CI JDK matrix, cached per-test timing
  history, and added an aggregate JaCoCo coverage badge.

### Fixed

- Made runtime tracking safer and more accurate by avoiding tracking-agent callback recursion,
  retaining pre-test class loads as conservative ambient dependencies, and attributing classes
  loaded from reactor JARs to their owning modules.
- Hardened self-hosted CI selection by preventing partial indexes, handling JDK 25 tracking
  fallback, reusing reachable stale indexes conservatively, and refreshing the main index after
  a cache fallback.
- Preserved Surefire reports for timing collection and made the end-to-end Maven harness derive
  the plugin version from the current build.

## [0.3.0] - 2026-07-26

### Added

- Self-hosted Blastradius test selection in this repository's GitHub Actions workflow,
  including main-branch index caching and multi-module pull-request feedback.

### Fixed

- Maven reactor tracking now runs once per session, skips aggregator-only projects, and avoids
  shaded-class collisions while discovering self-hosted tests.

## [0.1.0] - 2026-07-19

### Added

- Maven Central-ready `blastradius-maven-plugin` distribution.
- Shaded, relocated JGit and Jackson dependencies, including the embedded tracking agent.
- Maven Invoker integration coverage for track, select, fallback, and consumer-classpath isolation.
- CI, tagged-release, and dependency-update automation.

### Compatibility

- Java 21 and Maven 3.9.6 or newer are required.
- Published coordinate: `io.github.baokhang83.blastradius:blastradius-maven-plugin:0.1.0`.
