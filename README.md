<img width="1983" height="793" alt="image" src="https://github.com/user-attachments/assets/b8564f09-680a-4fd5-9983-611789e42975" />

# Blastradius

Most "test impact analysis" tools guess from a static, per-module dependency graph, or
train something probabilistic on historical flakiness. Blastradius does neither: a
`-javaagent` observes every class *actually loaded* while each test runs, records it, and
uses that real, per-test dependency map to decide what to run next time. No training data,
no heuristics, no opaque score.

## Proven on real projects, not just fixtures

Before any of this was trusted to actually skip tests in CI, it ran in shadow mode against
real open-source history and had every decision checked against ground truth:

| Project | Real history | Commit pairs analyzed | Would-miss cases | Savings |
|---|---|---|---|---|
| [commons-io](https://commons.apache.org/proper/commons-io/) | 6,230 commits | 5 | **0** | 41.2% of test executions correctly skipped |
| [jsoup](https://jsoup.org/) | 2,455 commits | 100 | **0** | 2.0% skipped (83% of this window was non-source maintenance commits — correctly triggering the safe fallback, not a mechanism weakness) |

**105 real commit pairs across two independent, unmodified production codebases. Zero
missed test failures.** Full analysis and how three real mechanism bugs were found and
fixed along the way (a hardcoded `argLine`, a JaCoCo collision, a parameterized-test
name mismatch) is in [`SESSION.md`](SESSION.md).

## How it works

1. **Track.** On a build of your base branch, a `java.lang.instrument` agent watches every
   class actually loaded while each test runs and records which production classes it
   really touched — ground truth, not a guess.
2. **Diff.** On every other build, the current commit is diffed against your base
   reference: Java source changes vs. everything else (config, resources, `pom.xml`,
   migrations).
3. **Select.** A test runs if one of its tracked dependencies changed, it's new or was
   itself modified, or a non-source change triggered the conservative "just run
   everything" fallback.
4. **Gate.** The selection narrows Surefire/Failsafe via the standard `-Dtest=` filter —
   nothing exotic, nothing that fights JaCoCo or a custom `argLine`.

## Modules

| Module | What it is | Status |
|---|---|---|
| **[`blastradius-core`](blastradius-core)** | The shared engine — the dependency-tracking agent and the selection rules (dependency match, conservative fallback, always-select-new/modified). Built and proven first; reused unmodified by both modules below. | Complete, 41 tests |
| **[`blastradius-maven-plugin`](blastradius-maven-plugin)** | **The product.** A real, installable `blastradius:select` Maven goal that gates CI by actually skipping tests during a live build. See its own [README](blastradius-maven-plugin/README.md) for adoption, configuration, and console output reference. | Complete, 46 tests |
| **[`blastradius-validator`](blastradius-validator)** | The shadow-mode harness that produced the real-project numbers above — replays a project's own commit history, compares what would have been skipped against ground truth, and reports would-miss cases. Still here if you want to validate the mechanism against a project of your own before adopting the plugin. | Complete, 60 tests |

## Quick start

```xml
<plugin>
  <groupId>io.github.baokhang83.blastradius</groupId>
  <artifactId>blastradius-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
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

No other change required — Surefire/Failsafe stay configured exactly as they already are.
See [`blastradius-maven-plugin/README.md`](blastradius-maven-plugin/README.md) for the full
configuration reference, what each build mode (`TRACK`/`SELECT`/`FALLBACK`) prints, and how
to set it up in CI.

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

- A class loaded only inside a JUnit 5 `@BeforeAll` is never attributed to any specific
  test — tracking only attributes loads to tests that are actually executing. If such a
  class changes and breaks a test that depended on it only via `@BeforeAll` setup, that
  dependency is invisible to selection. Narrow, deterministic, and documented — not a bug
  being hidden. Lean on the recommended daily full-suite run if this matters for your
  project.
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
