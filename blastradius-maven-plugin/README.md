# blastradius-maven-plugin

A Maven plugin that gates CI by actually skipping tests that are safe to skip for a given
change — dependency-tracking-driven test selection with conservative fallback rules, faster
builds without weakening the build's trustworthiness as a gate.

See `specs/002-ci-gating-plugin/` (spec, plan, research, contracts) for the full design.

## Prerequisites

- A Maven/JUnit 5 project.
- `blastradius-maven-plugin` installed into a repository your build can resolve it from.
- A configured base git reference (typically your default branch, e.g. `main`).

## Adopting the plugin

Add the goal to the project's `pom.xml` (see
`specs/002-ci-gating-plugin/contracts/mojo-and-index-contract.md` for the full configuration
reference):

```xml
<plugin>
  <groupId>io.github.baokhang83.blastradius</groupId>
  <artifactId>blastradius-maven-plugin</artifactId>
  <version>...</version>
  <executions>
    <execution>
      <phase>process-test-classes</phase>
      <goals><goal>select</goal></goals>
    </execution>
  </executions>
  <configuration>
    <baseRef>main</baseRef>
  </configuration>
</plugin>
```

No other change is required — Surefire/Failsafe stay configured exactly as before.

## What happens on the first build

There's no persisted dependency index yet. What happens depends on *whose* build this is:

- **A build of `baseRef` itself** (the common case — your first build with the plugin enabled
  is usually a trunk/post-merge build): the goal runs in `TRACK` mode. The full suite runs,
  exactly as it always has, and a fresh index is built alongside it (via an independent `mvn
  test` subprocess, agent-attached) for next time.
- **A PR/feature-branch build** (no trunk track run has happened yet): the goal runs in
  `FALLBACK` mode. The full suite still runs, safely — but no index is produced from this
  build, since a branch commit is a poor anchor for the shared index. The next trunk build is
  what establishes the real one.

Either way, this first build is not faster — that's expected and safe. It only becomes fast
once a trunk build has produced an index for PR builds to use.

## What happens on later builds

With an applicable index available (produced by a prior `TRACK` build on `baseRef`), a PR build
runs in `SELECT` mode: it diffs the current changes against `baseRef`, computes which tests are
affected, and narrows Surefire to that set. The console summary shows what happened:

```
[blastradius] SELECT — index built from a1b2c3d (2026-07-09T10:03:00Z)
[blastradius] 41 / 96 tests selected (57.3% skipped)
```

A build of `baseRef` itself always stays in `TRACK` mode, every time — it's the source of truth
the index is anchored to, not a consumer of it. If the persisted index ever goes stale (missing,
unreadable, or its anchor commit is no longer reachable, e.g. after a history rewrite), a PR
build safely falls back to `FALLBACK` rather than crashing or misapplying a stale selection.

Multi-module Maven reactors are supported: the goal runs once per module, computing and
applying an independent, correct filter for each — cross-module dependencies are attributed
correctly because tracking is based on actual class loads, not a static per-module graph.

## Reading the result

- **Build passes, fewer tests ran than the full suite**: expected, common case — the skipped
  tests were determined safe to skip for this change.
- **Build fails**: a selected test failed, exactly as it would have in an unmodified full run —
  the plugin never changes what causes a build to fail, only which tests get the chance to.
- **PR builds never speed up (always `FALLBACK`)**: no trunk `TRACK` build has produced an index
  yet, or the persisted index isn't surviving between builds — on CI, this usually means the
  index file isn't being cached/restored between runs; check that your trunk CI job actually
  runs the plugin (a build of `baseRef`), and that `.blastradius/index.json` is cached the same
  way you'd already cache `~/.m2` or a Gradle build cache.

## Auditing a specific test's decision

Every decision traces to a concrete reason — see a specific test's outcome in the per-build
JSON report (`.blastradius/last-build-report.json`), or run with `-Dblastradius.explain=true`
for an expanded console listing, rather than needing to re-run anything.

## Why this is safe to trust

This plugin reuses `blastradius-core`'s already-built and tested dependency-tracking mechanism
and selection rules unmodified (dependency match, fallback-on-unsound-changes, always-select-
new/modified) — not a new, unvalidated reimplementation. Soundness is a strong default here,
not an absolute guarantee: **we recommend every adopting team also run their full test suite
portfolio on a regular cadence (recommended: daily)**, so that even an occasional gap in what
the plugin selects is caught within a day rather than never. Treat the plugin as a fast,
sound-by-default first pass, and the daily full run as the backstop that actually closes the
loop — not the other way around.

See `specs/002-ci-gating-plugin/quickstart.md` for the full adoption walkthrough.
