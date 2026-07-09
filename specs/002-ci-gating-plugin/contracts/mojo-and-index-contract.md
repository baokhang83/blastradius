# Contract: `blastradius:select` Goal, Persisted Index, and Build Report

This plugin's external interface is (a) the Maven goal an adopting project's `pom.xml`
configures, (b) the persisted dependency index file that survives between builds, and (c) the
build report surfaced from a single invocation. All three are the contract other tooling (CI
scripts, adopting teams) can depend on.

## Goal invocation

```xml
<plugin>
  <groupId>io.github.baokhang83.blastradius</groupId>
  <artifactId>blastradius-maven-plugin</artifactId>
  <version>...</version>
  <executions>
    <execution>
      <id>select-tests</id>
      <phase>process-test-classes</phase>   <!-- before Surefire's default-test execution -->
      <goals>
        <goal>select</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <baseRef>main</baseRef>              <!-- required: the base git reference (FR-003) -->
    <indexPath>.blastradius/index.json</indexPath>  <!-- optional; this is the default -->
  </configuration>
</plugin>
```

Optional command-line escape hatch: `-Dblastradius.mode=track` forces `TRACK` mode regardless
of whether the current commit is the base reference (research.md #1) — for a team that wants to
explicitly pre-warm the index outside of an ordinary base-ref build.

No other change to the project's `pom.xml` is required (FR-012) — Surefire/Failsafe continue
to be configured exactly as they already are; the goal only ever narrows *which* tests they run
via the standard `-Dtest=` mechanism (research.md #1), never anything else about how they run.

**Outcome of one invocation**: the goal always leaves the build in one of three states before
Surefire's own `test` phase execution:

| State | Condition | Effect |
|---|---|---|
| `SELECT` | Not a base-ref build, and a valid, applicable `DependencyIndex` was found (data-model.md's `IndexApplicability = APPLICABLE`) | The project's effective Surefire test filter is narrowed to the selected test set |
| `TRACK` | The current commit resolves to `baseRef` itself, or `-Dblastradius.mode=track` was passed | No filter is applied — Surefire runs its full, normal set — and a subprocess `mvn test` (agent-attached) is forked to (re)build the index for next time (research.md #1) |
| `FALLBACK` | Not a base-ref build, and no applicable index (missing, unreadable, or its anchor commit is unreachable) | No filter is applied — Surefire runs its full, normal set — but *no* subprocess is forked; this build's own commit is a poor anchor for the shared index, so nothing is gained by paying that cost here (research.md #1) |

In all three states, the goal MUST NOT cause the build to fail on its own — `TRACK` and
`FALLBACK` are both normal, expected conditions (spec.md User Story 4), not errors.

## DependencyIndex JSON schema

Persisted at `indexPath` (default `.blastradius/index.json`), corresponding to the
`DependencyIndex` entity in data-model.md:

```json
{
  "anchorCommit": "sha",
  "builtAt": "ISO-8601 timestamp",
  "testDependencies": [
    {
      "test": { "className": "string", "methodName": "string | null" },
      "dependsOnClasses": ["string"]
    }
  ]
}
```

## BuildReport JSON schema

Written alongside the index (e.g. `.blastradius/last-build-report.json`) and also rendered as a
human-readable summary to the build's own console output (FR-008, FR-009):

```json
{
  "mode": "TRACK | SELECT | FALLBACK",
  "indexApplicability": "APPLICABLE | MISSING | UNREADABLE | ANCHOR_UNREACHABLE",
  "totalCount": "integer",
  "selectedCount": "integer",
  "decisions": [
    {
      "test": { "className": "string", "methodName": "string | null" },
      "selected": "boolean",
      "reason": "DEPENDENCY_MATCH | FALLBACK_NON_SOURCE_CHANGE | NEW_OR_MODIFIED_TEST | NO_MATCH",
      "matchedChangedClass": "string | null"
    }
  ]
}
```

`decisions` is empty when `mode = TRACK` or `mode = FALLBACK` — both run everything
unconditionally (neither is computing a per-test selection); `totalCount`/`selectedCount` are
still populated (`selectedCount = totalCount` in both cases). Only `TRACK` also forks a
subprocess and populates a fresh index; `FALLBACK` does neither (research.md #1).

## Console summary (rendered, not authoritative)

```
[blastradius] SELECT — index built from a1b2c3d (2026-07-09T10:03:00Z)
[blastradius] 41 / 96 tests selected (57.3% skipped)
[blastradius]   dependency-matched: 33, new-or-modified: 8, fallback: 0
[blastradius] Skipped test detail: run with -Dblastradius.explain=true for the full per-test reasoning
```

Same non-authoritative relationship to the JSON BuildReport as the validator's own text summary
has to its `AnalysisReport` (spec 001's contract) — a rendering, not a second source of truth.

## Invariants (validated by contract tests, not just documented)

- `selectedCount = totalCount` whenever `mode = TRACK` or `mode = FALLBACK` (FR-007 — both run
  everything).
- `decisions` is non-empty if and only if `mode = SELECT`.
- `updatedIndex` (data-model.md) is present if and only if `mode = TRACK` — `FALLBACK` never
  produces or refreshes an index (research.md #1).
- Every `decisions[].selected = true` entry has a `reason` other than `NO_MATCH`; every
  `selected = false` entry has `reason = NO_MATCH` (mirrors the validator's own
  `SelectionDecision` contract, spec 001).
- A build's actual Surefire/Failsafe execution outcome (pass/fail) for every *selected* test is
  never altered or suppressed by this plugin (FR-011) — the plugin's only effect on the build is
  which tests Surefire is told to run, never their outcome.
- `indexApplicability = APPLICABLE` if and only if `mode = SELECT` (the two are the same
  decision, expressed for two different audiences — a machine-checked condition and a
  human-readable summary).
