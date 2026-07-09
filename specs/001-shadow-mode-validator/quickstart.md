# Quickstart: Shadow-Mode Test Selection Validator

## Purpose

Answer, on real historical data, whether dependency-based test selection is safe (zero
would-miss cases) and worthwhile (meaningful execution savings) — before any further
investment in Blastradius. See `spec.md` for the full feature spec and
`../../docs/RELEASING.md`-style context in the constitution for why this gate exists.

## Prerequisites

- A local git clone of the target project (a real, Maven-built, JUnit 5 project), with full
  history available.
- JDK 21+ and Maven available on the machine running the validator (used both to build this
  tool and, as a subprocess, to build/test the target project).

## Running a validation

```bash
blastradius-validator run \
  --project-path /path/to/target-project \
  --commits 200 \
  --report-out ./report.json
```

This will, for each of the 200 most recent consecutive commit pairs on the target project's
default branch:

1. Check out the pair (via JGit, non-destructively).
2. Compute changed files/classes and the resulting selection decision per test.
3. Run the target project's own `mvn test`/`mvn verify` at the head commit, with the
   dependency-tracking agent attached, to get ground truth.
4. Re-run any failed test once to confirm it isn't flaky.
5. Compare selection against confirmed ground truth.

When it completes, it prints a text summary to stdout and writes the full `report.json`.

## Reading the result

- **Exit code `0`** → verdict `PASS`. Zero would-miss cases across the whole window. Check
  `savingsSummary.proportionSkipped` in the report to see whether the savings justify building
  the real product.
- **Exit code `1`** → verdict `FAIL`. Inspect `wouldMissCases` in the report — each entry names
  the exact commit, test, and why selection excluded it. This is a soundness bug in the
  selection approach (or in this validator) and must be understood before proceeding.
- **Exit code `2`** → the run itself could not complete (bad input, not a Maven/JUnit 5
  project). Not a verdict either way.

## Choosing `--commits`

There's no fixed default (FR-012) — pick a window based on the target project's suite runtime.
A suite that takes 2 minutes per full run makes a 200-commit window (~400 full runs counting
confirmation re-runs) feasible in a few hours; a 30-minute suite makes the same window a
multi-day job. Start small (10-20 commits) to confirm the tool runs end-to-end against your
chosen target, then scale up the window for the real validation evidence.

## Choosing a target project

Not covered by this feature (see spec.md Assumptions) — pick any real, actively-developed,
Maven/JUnit 5 project with a meaningfully slow suite (the whole point is proving savings on a
suite where savings matter).
