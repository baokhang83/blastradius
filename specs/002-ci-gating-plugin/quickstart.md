# Quickstart: CI-Gating Maven Plugin

## Purpose

Let a real Java/Maven project actually skip tests that are safe to skip for a given change —
not just report on what it would skip (that's what the shadow-mode validator already proved
sound; see `specs/001-shadow-mode-validator/`). This is the payoff: faster builds, without
weakening the build's trustworthiness as a gate.

## Prerequisites

- A Maven/JUnit 5 project (same scope as the validator — see spec.md Assumptions).
- `blastradius-maven-plugin` installed into a repository your build can resolve it from.
- A configured base git reference (typically your default branch, e.g. `main`).

## Adopting the plugin

Add the goal to the project's `pom.xml` (see `contracts/mojo-and-index-contract.md` for the
full configuration reference):

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

There's no persisted dependency index yet. What happens depends on *whose* build this is
(research.md #1):

- **If it's a build of `baseRef` itself** (the common case — your first build with the plugin
  enabled is usually a trunk/post-merge build): the goal runs in `TRACK` mode. The full suite
  runs, exactly as it always has, and a fresh index is built alongside it for next time.
- **If it's a PR/feature-branch build** (no trunk track run has happened yet): the goal runs in
  `FALLBACK` mode. The full suite still runs, safely — but no index is produced from this build,
  since a branch commit is a poor anchor for the shared index. The next trunk build is what
  establishes the real one.

Either way, this build is not faster — that's expected and safe (FR-007). It only becomes fast
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
the index is anchored to, not a consumer of it.

## Reading the result

- **Build passes, fewer tests ran than the full suite**: expected, common case — the skipped
  tests were determined safe to skip for this change (User Story 1).
- **Build fails**: a selected test failed, exactly as it would have in an unmodified full run
  (User Story 2, FR-011) — the plugin never changes what causes a build to fail, only which
  tests get the chance to.
- **PR builds never speed up (always `FALLBACK`)**: no trunk `TRACK` build has produced an index
  yet, or the persisted index isn't surviving between builds — on CI, this usually means the
  index file isn't being cached/restored between runs (research.md #2); check that your trunk
  CI job actually runs the plugin (a build of `baseRef`), and that `.blastradius/index.json` is
  cached the same way you'd already cache `~/.m2` or a Gradle build cache.

## Auditing a specific test's decision

Every decision traces to a concrete reason (FR-009); see a specific test's outcome in the
per-build JSON report (`.blastradius/last-build-report.json`, or `-Dblastradius.explain=true`
for an expanded console listing) rather than needing to re-run anything.

## Why this is safe to trust

This plugin does not re-derive its own safety — it applies the exact same dependency-tracking
mechanism and selection rules (dependency match, fallback-on-unsound-changes, always-select-
new/modified) that the shadow-mode validator already proved has zero would-miss cases across
105 real commit pairs on two real open-source projects (T061). If that evidence ever needs
revisiting — a new selection rule, a change to the tracking mechanism itself — Constitution
Principle V requires it to earn shadow-mode trust again before this plugin can rely on it.
