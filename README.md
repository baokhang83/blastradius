<img width="1983" height="793" alt="image" src="https://github.com/user-attachments/assets/b67eafad-af92-4277-863f-c9bf237b0eea" />

# Blastradius

<a href="https://baokhang83.github.io/blastradius/"><img src="https://img.shields.io/badge/document-English-blue.svg" alt="EN docs" /></a>
<a target="_blank" href="https://central.sonatype.com/search?q=g:io.github.baokhang83.blastradius%20%20a:blastradius-maven-plugin"><img src="https://img.shields.io/maven-central/v/io.github.baokhang83.blastradius/blastradius-maven-plugin.svg?label=maven%20central" /></a>
<a target="_blank" href="https://www.oracle.com/technetwork/java/javase/downloads/index.html"><img src="https://img.shields.io/badge/JDK-21+-green.svg" /></a>
<a target="_blank" href="https://github.com/baokhang83/blastradius/actions/workflows/build.yml"><img src="https://github.com/baokhang83/blastradius/actions/workflows/build.yml/badge.svg" /></a>
<a target="_blank" href="https://github.com/baokhang83/blastradius"><img src="https://img.shields.io/github/languages/top/baokhang83/blastradius?cacheSeconds=86400" /></a>
<a target="_blank" href="https://github.com/baokhang83/blastradius"><img src="https://raw.githubusercontent.com/baokhang83/blastradius/refs/heads/gh-pages/badges/jacoco.svg" /></a>


Most "test impact analysis" tools guess from a static, per-module dependency graph, or
train something probabilistic on historical flakiness. Blastradius does neither: a
`-javaagent` observes every class *actually loaded* while each test runs, records it, and
uses that real, per-test dependency map to decide what to run next time. No training data,
no heuristics, no opaque score.

## Historical replay

200-pair replays against apache/shenyu, apache/httpcomponents-client and jhy/jsoup, each replaying
200 consecutive commits as the change each one introduced over the commit before it — with bounded
mutation validation enabled on the same run. A would-miss is a test that caught a real regression
or an injected mutant but that selection chose not to run.

| Project | Commit range | Commit pairs (excluded) | Would-miss | Test executions selected | Skipped |
| --- | --- | :---: | ---: | ---: | ---: |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/shenyu">shenyu</a></h4> | [`69cd1d5`](https://github.com/apache/shenyu/commit/69cd1d5721647a60007584983d96fc94452a4f6b) → [`3a411e0`](https://github.com/apache/shenyu/commit/3a411e017acfc47636e2bbfeb2958108d1f15a05) | 200 (0) | **0**<sup>1</sup> | 153,142 / 527,508 | **71.0%** |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/httpcomponents-client">httpcomponents-client</a></h4> | [`ef34bfa`](https://github.com/apache/httpcomponents-client/commit/ef34bfa8fd6f2f6181ba2e41051050ed877e56df) → [`4dae8da`](https://github.com/apache/httpcomponents-client/commit/4dae8da4a365f639e42fea84822285f345be7755) | 200 (12) | **0**<sup>2</sup> | 180,198 / 443,593 | **59.4%** |
| <h4><a href="https://github.com/jhy/jsoup">jsoup</a></h4> | [`b62e362`](https://github.com/jhy/jsoup/commit/b62e362d3945e86725c817101332b66277aff9e4) → [`9d2241f`](https://github.com/jhy/jsoup/commit/9d2241ff467d03accbf902a650adc60513bf5c11) | 200 (10) | **0**<sup>3</sup> | 193,635 / 351,882 | **45.0%** |
|<img width=400 />|  |  |  |  | **58.5%** |

Bounded mutation validation ran on the same window: for each pair it injects synthetic faults into
head and checks whether the tests selected catch them.

| Project | Mutants (compilable) | Mutants caught | Killing tests selected | Diff-targeted / fallback |
| --- | ---: | ---: | :---: | ---: |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/shenyu">shenyu</a></h4> | 872 (778) | 339 | **943 / 943** | 686 / 257 |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/httpcomponents-client">httpcomponents-client</a></h4> | 916 (916) | 551 | **75,380 / 75,380** | 3,571 / 71,809 |
| <h4><a href="https://github.com/jhy/jsoup">jsoup</a></h4> | 942 (938) | 652 | **44,798 / 47,866**<sup>4</sup> | 42,440 / 5,426 |

A "killing test" is one that actually caught an injected fault (passed on head, failed on the mutant, stayed failed on
confirmation), so it is a test the selection *must not* skip. Counts are per mutant, so a test that kills five mutants
counts five times. Across shenyu's 872 injected faults and httpcomponents-client's 916, selection never skipped a test
that would have caught one.

See [HISTORICAL_REPLAY_ANALYSIS.md](HISTORICAL_REPLAY_ANALYSIS.md) for footnotes 1–4 and the full per-project
analysis behind these numbers.

## How it works

1. **Track.** On a build of your base branch, a `java.lang.instrument` agent watches every
   class actually loaded while each test runs and records which production classes it
   really touched — ground truth, not a guess.
2. **Diff.** On every other build, the current commit is diffed from its merge base with your
   base reference. This isolates the PR's own JVM source changes (Java and conventional Kotlin)
   from changes that landed on the target branch after the PR diverged.
3. **Select.** A test runs if one of its tracked dependencies changed, it's new or was
   itself modified, or a non-source change triggered the conservative "just run
   everything" fallback.
4. **Gate.** The selection narrows Surefire/Failsafe via the standard `-Dtest=` filter —
   nothing exotic, nothing that fights JaCoCo or a custom `argLine`.

## Modules

| Module | What it is | Status |
|---|---|---|
| **[`blastradius-core`](blastradius-core)** | The shared engine — the dependency-tracking agent and the selection rules (dependency match, conservative fallback, always-select-new/modified). Built and proven first; reused unmodified by both modules below. | Complete, 118 tests |
| **[`blastradius-maven-plugin`](blastradius-maven-plugin)** | **The product.** A real, installable `blastradius:select` Maven goal that gates CI by actually skipping tests during a live build. See its own [README](blastradius-maven-plugin/README.md) for adoption, configuration, and console output reference. | Complete, 79 tests |
| **[`blastradius-validator`](blastradius-validator)** | The shadow-mode harness that produced the real-project numbers above — replays a project's own commit history, compares what would have been skipped against ground truth, and reports would-miss cases. Still here if you want to validate the mechanism against a project of your own before adopting the plugin. | Complete, 170 tests |

## Quick start

```xml
<plugin>
  <groupId>io.github.baokhang83.blastradius</groupId>
  <artifactId>blastradius-maven-plugin</artifactId>
  <version>0.3.2</version>
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

For separate CI runners, first persist and restore the workspace's `.blastradius/` directory
with your CI cache. S3 is optional; use these Maven settings only when an S3-compatible shared
store is a better fit for your runners:

```xml
<indexStore>s3</indexStore>
<s3Bucket>ci-dependency-indexes</s3Bucket>
<s3Prefix>blastradius</s3Prefix>
<s3Region>eu-central-1</s3Region>
<!-- <s3Endpoint>https://minio.example.com</s3Endpoint> optional -->
```

### Gradle

```groovy
plugins {
  id 'io.github.baokhang83.blastradius' version '0.3.0'
}

blastradius {
  baseRef = 'main'
}
```

There is no separate Gradle `select` task. Applying the plugin configures every Java `Test`
task, so run the normal `./gradlew test`: it tracks on `main` and selects the relevant tests on
other branches when a saved index is available.

For separate CI runners, first persist and restore `.blastradius/` with your CI cache. S3 is
optional; configure the same shared S3 index store only when needed:

```groovy
blastradius {
  baseRef = 'main'
  indexStore = 's3'
  s3Bucket = 'ci-dependency-indexes'
  s3Prefix = 'blastradius'
  s3Region = 'eu-central-1'
  // s3Endpoint = 'https://minio.example.com' // optional
}
```

No other change required — Surefire/Failsafe stay configured exactly as they already are.
See [`blastradius-maven-plugin/README.md`](blastradius-maven-plugin/README.md) for the full
configuration reference, what each build mode (`TRACK`/`SELECT`/`FALLBACK`) prints, and how
to set it up in CI.

### Sharing indexes across CI runners

By default, indexes stay under the workspace's `.blastradius/` directory. That directory is a
saved map of which production classes each test used. A trunk `TRACK` job writes the map; a PR
`SELECT` job restores it, compares the PR's changed classes to it, and runs only matching tests.

On fresh CI workers, preserve `.blastradius/` between the trunk and PR jobs with the CI cache
alongside your usual Maven dependency cache. **S3 is not required.** It is an alternative when
your runners cannot share a reliable cache: configure the Maven plugin or Gradle extension with
`indexStore = s3`, a bucket, and a region. The shared object store lets the PR job read the same
commit-keyed index that the trunk job wrote. Credentials come from the standard AWS credential
chain; do not put access keys in build files. If no saved index can be restored — for example,
on a first build or cache miss — Blastradius safely runs the full suite instead. See the
[Maven S3 configuration reference](blastradius-maven-plugin/README.md#shared-s3-index-store).

### GitHub Actions cache example

In the workflow that runs Blastradius-enabled tests, put the restore step after checkout and
before `mvn verify` or `./gradlew test`. Save only successful `main` builds, after the test
command, so pull requests always read a map made by the trusted base branch:

```yaml
steps:
  - uses: actions/checkout@v7

  - name: Restore Blastradius index
    id: blastradius-index
    uses: actions/cache/restore@v4
    with:
      path: .blastradius
      key: blastradius-index-${{ runner.os }}-${{ github.sha }}
      restore-keys: |
        blastradius-index-${{ runner.os }}-

  - run: mvn -B --no-transfer-progress verify

  - name: Save Blastradius index from main
    if: ${{ github.ref == 'refs/heads/main' && success() && steps.blastradius-index.outputs.cache-hit != 'true' }}
    uses: actions/cache/save@v4
    with:
      path: .blastradius
      key: blastradius-index-${{ runner.os }}-${{ github.sha }}
```

The key includes the commit SHA so a `main` build saves an immutable snapshot. A PR has a new
SHA, so its exact lookup misses; `restore-keys` then restores the newest compatible
`main` snapshot. Do not put credentials or other secrets under `.blastradius/` because GitHub
Actions caches are readable by pull-request workflows.

### This repository's CI

This repository self-hosts Blastradius in its own Maven workflow: CI first builds and installs
the plugin from the checkout, then runs the normal reactor with an internal CI-only Maven
profile. Successful `main` runs refresh the cached index; pull requests restore it and use the
same plugin code under review to select tests. The bootstrap and multi-module reporting mechanics
are intentionally kept in the workflow and feature design, not the adoption quick start above.

```bash
git clone https://github.com/baokhang83/blastradius.git
cd blastradius
mvn clean install   # builds and tests all three modules
```

## Multi-module reactors

Fully supported, without extra bookkeeping. Because tracking is based on actual class
loads rather than a static per-module dependency graph, a change in one module correctly
selects a *dependent* test living in another module — attribution falls out of the
mechanism itself.

## Kotlin/JVM support

Blastradius recognizes conventional Kotlin source roots — `src/main/kotlin` and
`src/test/kotlin` — alongside their Java equivalents. A changed `Greeting.kt` contributes both
the ordinary `Greeting` name and Kotlin's generated `GreetingKt` file facade; recorded nested
or lambda classes such as `GreetingKt$format$1` are attributed to that stable source root.

Kotlin inline functions are deliberately conservative. Their bodies are copied into callers, so
there may be no stable class load to attribute to the changed source file. If either side of a
Kotlin change contains an inline function, Blastradius runs the full suite instead of narrowing.

Custom `@file:JvmName` facades and Kotlin source files whose emitted class names do not follow
their file names are outside this filename-based mapping. Keep the recommended regular full-suite
run for those projects and for any other compiler-generated edge case.

## Why this is safe to use

The selection mechanism is sound by default, not by absolute guarantee — see the
real-project numbers above for what "sound by default" has actually measured out to.
**We recommend every adopting team also run their full test suite portfolio on a regular
cadence (recommended: daily)** as a complementary safety net, so even an occasional gap is
caught within a day rather than never. That combination — fast, sound-by-default selection
on every build, backstopped by a full run — is the intended trust model, not either one
alone.

## Design principles (project constitution, v2.0.0)

- **Test-Driven Development is non-negotiable.** Every piece of engine code was built
  red → green → refactor; a tool that decides which tests to skip cannot itself be
  undertested.
- **Clean code & simplicity.** No speculative abstraction — `blastradius-core` was
  extracted only once a second real consumer (the plugin) needed it.
- **Safety over speed.** Sound, conservative selection is the strong default, complemented
  by the recommended daily full-suite run above, not a substitute for one.
- **Deterministic core before ML.** Selection is pure, explainable dependency tracking,
  requiring zero historical/training data and correct from a project's very first run —
  no machine learning, no probabilistic shortcuts.
- **Explainability.** Every decision carries a concrete reason — which changed class a
  test's tracked dependencies intersect with, or which fallback rule fired — never an
  opaque score.
- **Maintainable, modern foundations.** JUnit 5 Platform, current JDK, no deprecated APIs
  or abandoned tooling.

Full text and rationale: [`.specify/memory/constitution.md`](.specify/memory/constitution.md).

## Known limitations

- A class reached only through a **string-dispatched API** may go unattributed:
  if the call is `select("div > p")` and the parse behind it sits on a cached path, the calling test
  never records a load of the parser it truly depends on. Measured, not hypothetical — this is what
  left 750 `QueryParser`-dependent tests unselected in the jsoup replay above. It bites hardest
  where the central API is a string DSL: selector engines, expression languages, query parsers. If
  that describes your project, weigh the daily full-suite run accordingly.
- Refreshing the dependency index (a "track" build) runs the full suite once; correct, but
  not optimized for very slow suites. It only happens on base-reference builds, never on
  every PR build.

## Project layout

```
blastradius-core/           the engine: tracking agent + selection rules
blastradius-maven-plugin/   the product: the blastradius:select goal
blastradius-validator/      shadow-mode validation harness (real-project evidence above)
specs/                      spec, plan, research (ADR-style), contracts, tasks — per feature
.specify/memory/            project constitution
SESSION.md                  narrative log of how T061's real-project validation went
```

## License

[Apache License 2.0](LICENSE).
