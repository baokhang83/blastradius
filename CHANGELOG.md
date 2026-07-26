# Changelog

All notable changes to this project are documented in this file.

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
