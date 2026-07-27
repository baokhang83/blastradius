# Session: core: persist ambient set through record writer/reader

- **intent:** core: persist ambient set through record writer/reader
- **started:** 2026-07-26

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

## Knowledge transfer

- `DependencyRecordFile`/`DependencyRecordSet`: two distinct types with a similar shape but different jobs — `DependencyRecordFile` is the private on-disk JSON shape for one `<prefix>.<pid>` file; `DependencyRecordSet` is the public in-memory result of merging every sibling file's tests and ambient sets in `readAll`. Kept separate rather than reusing one type across both roles, since the merged/queryable shape and the raw per-file JSON shape are allowed to diverge independently. Status: documented.
- `DependencyRecordReader.readAll` changed its return type from a bare `Map<TestIdentity, Map<String,String>>` to `DependencyRecordSet` — every existing caller (`TrackRunner`, `RunCommand` in blastradius-validator, `WriteTrackingIndexAction` in blastradius-gradle-plugin, `EndToEndTestSupport`, `DependencyTrackingIntegrationTest`, `MavenBuildRunnerTest`) needed a one-line `.tests()` unwrap; the gradle plugin's own `DependencyIndex` class (same simple name, different package) was untouched since it isn't the type this feature is changing. Status: documented.
- `DependencyIndexFormat.CURRENT_VERSION` bumped 1 → 2 for the new `ambientDependencies` field; `IndexApplicabilityResolver`'s existing `hasCurrentFormat()` check (unchanged) now naturally rejects any index still at version 1 or migrated-legacy version 1, forcing the safe TRACK/fallback path rather than trusting a possibly-absent ambient set. Status: documented.

## Decision: changed DependencyRecord on-disk shape from bare JSON array to an object wrapper

- **where:** `blastradius-core/.../tracking/DependencyRecordFile.java, DependencyRecordWriter.java, DependencyRecordReader.java`
- **why:** the per-JVM record file is an ephemeral intra-build protocol between the writer and reader, never persisted across builds, so its shape can change freely without a version-migration story — unlike DependencyIndex
- **alternative:** keep the flat array and smuggle the ambient set into a sentinel DependencyRecord entry — rejected: overloads a type that already means 'one test's dependencies' with an unrelated fork-wide concept
- **trust:** ⚠ not independently verified

## Decision: decoupled legacy index migration target from CURRENT_VERSION; kept a 3-arg DependencyIndex convenience overload

- **where:** `blastradius-core/.../index/DependencyIndexFormat.java (PRE_AMBIENT_DEPENDENCIES_VERSION), blastradius-maven-plugin/.../index/DependencyIndex.java`
- **why:** migrateLegacyVersion mapping straight to CURRENT_VERSION would let a legacy index masquerade as having the new ambientDependencies field (silently empty) instead of correctly failing hasCurrentFormat() and falling back; the 3-arg overload avoids touching ~20 existing tests that don't exercise ambient behavior, mirroring the record's existing formatVersion-defaulting convenience constructor
- **alternative:** bump every old test call site to pass Set.of() explicitly — rejected: pure churn across files unrelated to this feature, no behavioral coverage gained
- **constitution:** Principle III (Safety Over Speed)
- **trust:** ⚠ not independently verified
