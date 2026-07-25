# Session: Route Gradle tracking and selection through the configured store

- **intent:** Route Gradle tracking and selection through the configured store
- **started:** 2026-07-25

<!--
FluencyLoop Stage 3 — a session is a slice of the build. One block per meaningful decision,
appended at the slice boundary as it's taught. No `commits:` field: the feature is a branch,
so the PR view derives commits live from git.

Each decision is a `## Decision:` heading followed by a bullet list — one bullet per field, so
it renders one-per-line as real Markdown (plain `key: value` lines collapse into a single
paragraph when rendered). Fields:

  where        — file/area the decision lives in (NOT a line number — survives refactoring)
  why          — the rationale, taught live before it was written
  alternative  — the rejected option and why (this is what makes it rationale, not description)
  design       — (optional) ../design.md#anchor — the diagram this decision shaped or used
  constitution — (optional) §N — the principle this decision serves or trades off against
  trust        — ✓ verified | ⚠ not independently verified  (about the DECISION, never the person)

Delete this comment and the example below once real decisions land.
-->

---

## Decision: <chose X over Y>

- **where:** `<path/to/File.ext>`
- **why:** <the one-line why, engaged with — not post-hoc narration>
- **alternative:** <the rejected option> — rejected: <why>
- **trust:** ⚠ not independently verified

## Decision: create S3 clients only inside Gradle task actions

- **where:** `blastradius-gradle-plugin task actions`
- **why:** Configuration stays as serializable values while TRACK and SELECT create and close the remote client at execution time.
- **alternative:** Create an S3 client during Gradle configuration — rejected: live credentials and network state would leak into configuration-cache inputs.
- **design:** ../design.md#sequence-track-on-one-runner-select-on-another
- **constitution:** §IV
- **trust:** ✓ verified
