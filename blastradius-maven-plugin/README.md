# blastradius-maven-plugin

**Stop running your whole test suite for a one-line change.**

`blastradius-maven-plugin` narrows `mvn test` to just the tests that could possibly be
affected by the current change — using real, observed class-level dependency data, not a
guess. When it isn't sure, it runs everything. It never changes what causes your build to
fail, only how many tests get the chance to.

```
[blastradius] SELECT — index built from 4e02156 (2026-07-10T03:03:04Z)
[blastradius] 1 / 2 tests selected (50.0% skipped)
[blastradius]   dependency-matched: 1, new-or-modified: 0, fallback: 0
```

## Contents

- [How it works](#how-it-works)
- [Install](#install)
- [Generated goal reference](#generated-goal-reference)
- [Configuration reference](#configuration-reference)
- [What you'll see in the Maven output](#what-youll-see-in-the-maven-output)
- [The files it writes](#the-files-it-writes)
- [Multi-module reactors](#multi-module-reactors)
- [Reading a build's result](#reading-a-builds-result)
- [Why this is safe to trust](#why-this-is-safe-to-trust)
- [Known limitations](#known-limitations)

## How it works

1. **Track.** On a build of your base branch (e.g. a post-merge/trunk build), a
   `-javaagent` observes every class actually loaded while each test runs and records a
   fresh dependency map — which test touched which production classes. This runs as an
   independent `mvn test` subprocess; your own build's Surefire execution is completely
   untouched by it.
2. **Diff.** On every other build, the goal finds the merge base of the current commit and
   `baseRef`, then diffs from that common ancestor to `HEAD`. This excludes target-branch
   changes that landed after the branch diverged while still classifying JVM source changes
   (Java and conventional Kotlin) versus config, resources, `pom.xml`, and migrations.
3. **Select.** A test runs if **(a)** one of its tracked dependencies changed, **(b)** it's
   new or was itself modified, or **(c)** a non-source-code change triggered the
   conservative "just run everything" fallback.
4. **Filter.** The selection is handed to Surefire/Failsafe as a standard `-Dtest=` filter
   — nothing exotic, nothing that fights JaCoCo or a custom `argLine`.

Three modes fall out of this, decided fresh on every invocation — never configured by hand:

| Mode | When | What happens |
|---|---|---|
| **`TRACK`** | Current commit *is* `baseRef` (or `-Dblastradius.mode=track`) | Full suite runs, untouched. A subprocess rebuilds the index in the background for next time. |
| **`SELECT`** | Not `baseRef`, and a usable index exists for the merge base | Surefire is narrowed to the affected tests. This is the fast path. |
| **`FALLBACK`** | Not `baseRef`, and no usable index (missing, unreadable, unreachable, for another baseline commit, or no common ancestor) | Full suite runs, untouched. Nothing gained by tracking from a throwaway branch commit, so nothing is forked. |

`TRACK` and `FALLBACK` are never treated as errors — they're the plugin being honest about
not having enough information yet, and defaulting to safe.

## Install

Requires a Maven/JUnit 5 project and a resolvable repository for the plugin artifact.

```xml
<plugin>
  <groupId>io.github.baokhang83.blastradius</groupId>
  <artifactId>blastradius-maven-plugin</artifactId>
  <version>0.1.0</version>
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

That's the whole change. Surefire/Failsafe stay configured exactly as they already are —
the goal only ever narrows *which* tests they run, bound early enough
(`process-test-classes`) to take effect before Surefire's own `test` phase.

Your first build (whichever branch it happens to run on) will be `TRACK` or `FALLBACK`,
not `SELECT` — there's no index yet. It becomes fast once a `baseRef` build has produced
one. See [Reading a build's result](#reading-a-builds-result) if it seems stuck there.

## Generated goal reference

The plugin's Maven-generated reference lists every goal and parameter. Generate it from
the repository root with:

```sh
mvn -pl blastradius-maven-plugin -am site
```

Then view `blastradius-maven-plugin/target/site/plugin-info.html`. Maven also generates a
separate page for each goal, including `help-mojo.html` and `select-mojo.html`.

## Configuration reference

| Parameter | CLI property | Default | Required | Meaning |
|---|---|---|---|---|
| `baseRef` | `-DbaseRef` | — | **yes** | The target git reference (`main`, `origin/main`, a tag — anything JGit can resolve). Non-target builds compare from its merge base with `HEAD`. |
| `indexPath` | `-DindexPath` | `.blastradius/index.json` | no | Root-relative index-file template. The merge-base SHA is inserted before its filename for `SELECT`, so the default produces `.blastradius/<full-sha>/index.json`. Rejected if it resolves outside the project directory. |
| — | `-Dblastradius.mode=track` | — | no | Force `TRACK` regardless of what commit you're on — for explicitly pre-warming the index outside an ordinary trunk build. |
| — | `-Dblastradius.explain=true` | `false` | no | Print the full per-test decision listing to the console, not just the aggregate summary. |

`indexPath` is one shared index namespace for the whole reactor (see
[Multi-module reactors](#multi-module-reactors)) — configure it once at the root; every
module's own goal execution resolves the same commit-keyed path.

### CI setup

- **Trunk/post-merge job**: run as normal — no extra flags needed, `TRACK` is automatic.
- **PR job**: run as normal too. Just make sure `.blastradius/` is cached and
  restored between CI runs the same way you already cache `~/.m2` — it has to survive
  between the trunk job that wrote it and the PR job that reads it, or every PR build stays
  in `FALLBACK` forever (see below).

## What you'll see in the Maven output

**`TRACK`** — a base-branch build, building/refreshing the index:

```
[INFO] --- blastradius:0.1.0:select (default) @ your-project ---
[INFO] [blastradius] TRACK — building a fresh index
[INFO] [blastradius] 2 / 2 tests selected (0.0% skipped)
```

`selectedCount == totalCount` here isn't the plugin "selecting everything" — `TRACK` never
computes a selection at all, it just reports how big the suite it ran full was.

**`SELECT`** — a PR build, narrowed by a real index:

```
[INFO] --- blastradius:0.1.0:select (default) @ your-project ---
[INFO] [blastradius] SELECT — index built from 4e02156 (2026-07-10T03:03:04Z)
[INFO] [blastradius] 1 / 2 tests selected (50.0% skipped)
[INFO] [blastradius]   dependency-matched: 1, new-or-modified: 0, fallback: 0
[INFO] [blastradius] Skipped test detail: run with -Dblastradius.explain=true for the full per-test reasoning
```

Add `-Dblastradius.explain=true` for the per-test breakdown that line is pointing you at:

```
[INFO] [blastradius]   com.example.CalculatorTest#addsTwoNumbers — selected reason=DEPENDENCY_MATCH matchedChangedClass=com.example.Calculator
[INFO] [blastradius]   com.example.GreeterTest#greetsByName — skipped reason=NO_MATCH
```

**`FALLBACK`** — no usable index, running safe:

```
[INFO] --- blastradius:0.1.0:select (default) @ your-project ---
[INFO] [blastradius] FALLBACK — no persisted index found (MISSING)
[INFO] [blastradius] 2 / 2 tests selected (0.0% skipped)
```

The reason in parentheses always tells you *why*: `MISSING` (no TRACK build exists for the
resolved merge base), `UNREADABLE` (the index file is corrupt), `ANCHOR_UNREACHABLE` (its
recorded commit no longer exists, e.g. after a history rewrite), `ANCHOR_MISMATCH` (the stored
index declares a different baseline), `MERGE_BASE_UNAVAILABLE` (the refs have no common
ancestor), or `INTERNAL_ERROR` (the goal hit an unexpected fault mid-computation and safely
bailed out to a full run rather than crash the build or guess).

## The files it writes

Both live under `.blastradius/` at the reactor root — add it to `.gitignore`.

- **`<full-sha>/index.json`** — the persisted dependency map a `TRACK` build produces for its
  exact commit and a `SELECT` build reads for its resolved merge base. The default location is
  `.blastradius/<full-sha>/index.json`.
  `{ anchorCommit, builtAt, testDependencies: [{ test, dependsOnClasses }] }`.
- **`last-build-report.json`** — this build's own decisions, machine-readable.
  `{ mode, indexApplicability, decisions: [{ test, selected, reason, matchedChangedClass }], selectedCount, totalCount }`.
  This is the source of truth every console line above is a rendering of — audit a specific
  test's outcome here without re-running anything.

## Multi-module reactors

Fully supported. The goal runs once per module and computes an independent, correct
Surefire filter for each — a change in one module correctly selects a *dependent* test in
another module, because tracking is based on actual class loads observed at runtime, not a
static per-module dependency graph that would need separate bookkeeping to get right.

## Kotlin/JVM support

Kotlin sources in the standard Maven roots (`src/main/kotlin` and `src/test/kotlin`) participate
in the same track-and-select flow. For a changed `Greeting.kt`, Blastradius matches both the
source-root class (`Greeting`) and Kotlin's `GreetingKt` file facade. Compiler-generated nested
and lambda classes with names such as `GreetingKt$format$1` match their stable source root.

If either version of a changed Kotlin file contains an inline function, the goal runs the full
suite. Kotlin copies inline bodies into callers, so runtime class loads cannot reliably identify
every test affected by that source file.

Custom `@file:JvmName` facades and types whose emitted class names do not follow the source file
name are outside this filename-based mapping. Retain the regular full-suite run recommended below
when those patterns are in use.

## Reading a build's result

- **Fewer tests ran than the full suite, build passed** — the expected, common case. The
  skipped tests were determined safe to skip for this specific change.
- **Build failed** — a selected test failed, exactly as it would have in an unmodified full
  run. The plugin never changes *what* fails a build, only which tests get the chance to.
- **PR builds are always `FALLBACK`, never speeding up** — no trunk `TRACK` build has
  produced an index yet, or the index isn't surviving between CI runs. Check that your
  trunk job is actually a `baseRef` build with the plugin enabled, and that
  `.blastradius/` is cached/restored the same way `~/.m2` already is.

## Why this is safe to trust

The tracking mechanism and selection rules (dependency match, always-select-new/modified,
fallback-on-unsound-change) are `blastradius-core`'s already-built and tested engine, reused
unmodified here — not a new, unvalidated reimplementation. Soundness is a strong default,
not an absolute guarantee: **we recommend every adopting team also run their full test
suite portfolio on a regular cadence (recommended: daily)** as a backstop, so an occasional
gap in what the plugin selects is caught within a day rather than never. Treat this plugin
as a fast, sound-by-default first pass — the daily full run is what actually closes the
loop, not the other way around.

## Known limitations

- A class loaded only inside a JUnit 5 `@BeforeAll` is never attributed to any specific
  test — dependency tracking only attributes loads to tests that are actually executing. If
  such a class later changes and breaks a test that depended on it only via `@BeforeAll`
  setup, that dependency is invisible to selection. Narrow and deterministic, not a bug
  being hidden — lean on the daily full-suite run if this matters for your project.
- Refreshing the index (`TRACK`) runs the full suite once via a subprocess; correct, but not
  optimized for very slow suites. It only happens on `baseRef` builds, never on PR builds.

---

See `specs/002-ci-gating-plugin/` for the full spec, design decisions (ADR-style research
notes), and contract tests, and `specs/002-ci-gating-plugin/quickstart.md` for a narrative
walkthrough of the same adoption path.
