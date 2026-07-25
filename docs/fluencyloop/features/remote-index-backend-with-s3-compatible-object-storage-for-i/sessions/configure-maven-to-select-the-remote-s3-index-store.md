# Session: Configure Maven to select the remote S3 index store

- **intent:** Configure Maven to select the remote S3 index store
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

## Decision: ship the AWS SDK inside the Maven plugin

- **where:** `blastradius-maven-plugin/pom.xml and SelectMojo`
- **why:** S3 is opt-in through non-secret plugin settings, while shading keeps consumer dependency graphs isolated.
- **alternative:** Require adopters to supply a matching AWS SDK — rejected: consumer builds would become version-coupled to the plugin.
- **design:** ../design.md#class-diagram
- **constitution:** §VI
- **trust:** ✓ verified
