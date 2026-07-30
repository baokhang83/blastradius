# Session: add ProgressLogger and wire intermediate progress logging

- **intent:** add ProgressLogger and wire intermediate progress logging
- **started:** 2026-07-30

<!--
FluencyLoop Stage 3 — a session is a slice of the build. It holds two persistent records:
  1. Knowledge transfer — what the developer was made fluent in this slice (you write it).
  2. Decisions — the genuine forks, appended by `fluencyloop decision` (the script formats them).

Everything below is scaffolding in comments — nothing to delete. Write knowledge transfer under
its headings; add each decision with
  fluencyloop decision --where <file/area> --why <rationale> [--alternative <rejected + why>] \
                       [--title <chose X over Y>] [--constitution §N] [--trust verified|unverified]
so the block is formatted deterministically and you never hand-write the bullet schema. No
`commits:` field: the feature is a branch, so the PR view derives commits live from git.

KNOWLEDGE-TRANSFER — one bullet per component/role/mechanism explained:
  **<subject>** — <what it does, under what conditions> · status: documented | follow-up
  Make it RICH: cover the inventory AND the non-obvious, hard-won lessons (a bug's root cause,
  why something is done an odd way, a documented limitation). Describe the WORK, never a person
  (no competence, no "who knew what") — these files are committed and name an author via git.

DECISION fields (assembled by `fluencyloop decision`):
  where        — file/area (NOT a line number — survives refactoring)
  why          — the rationale, taught live before it was written
  alternative  — the rejected option and why (what makes it rationale, not description)
  design       — (optional) ../design.md#anchor
  constitution — (optional) §N
  trust        — ✓ verified | ⚠ not independently verified (about the DECISION, never the person)
-->

---

## Knowledge transfer

_The ground this slice makes understandable — components, roles, and conditions explained,
persisted so the fluency doesn't evaporate with the conversation. About the work, never a person._

### Components (role, conditions)

- **`ProgressLogger`** — emits timestamped, human-readable narration (window resolved, build start/end + duration, per-pair completion + would-miss count, final verdict/duration) while a run is in flight, so the jar stops being silent for the minutes a real run takes. Takes an injected `PrintStream` and a `LongSupplier` epoch-millis clock so tests drive both the sink and the timestamps deterministically. `toStderr()` is the production factory; `silent()` is a genuine no-op (discards to `nullOutputStream`, `enabled=false`). Marked thread-safe now (synchronized `line()`) because slice 2/3's concurrent build workers will call `buildStarted`/`buildFinished` from multiple threads. · status: documented
- **`ProgressLogger.duration(millis)`** — renders a span as `Xh Ym Zs` / `Ym Zs` / `Zs`, dropping leading zero units, and shows one decimal for sub-second spans (`0.5s`) so a fast step doesn't collapse to a bare `0s`. Package-private + unit-tested directly. · status: documented
- **`RunCommand` logging seam** — a second package-private constructor `RunCommand(ProgressLogger)` lets tests inject a silent/capturing logger; the public no-arg constructor defaults to `toStderr()`, so existing callers (Main, integration tests) are untouched. The `run()` loop now times each pair with `System.currentTimeMillis()` deltas and logs completion/exclusion; build-level start/end logging arrives in slice 3 with `CommitBuildService`. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Narration MUST go to stderr, never stdout** — the machine-readable `AnalysisReport` is written to `--report-out` and the text summary to stdout (`Main#printSummary`). Interleaving progress into stdout would corrupt anything piping/parsing the report or summary. stderr is the only stream free to carry human narration. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: progress narration to stderr, not stdout

- **where:** `blastradius-validator/.../cli/ProgressLogger.java`
- **why:** the AnalysisReport (--report-out) and text summary (stdout) must stay clean for piping/parsing; stderr is the only stream free to carry human narration
- **alternative:** interleave progress into stdout — rejected: corrupts anything parsing the report or summary
- **trust:** ✓ verified
