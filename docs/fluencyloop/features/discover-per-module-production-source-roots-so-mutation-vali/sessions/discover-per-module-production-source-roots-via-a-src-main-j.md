# Session: Discover per-module production source roots via a src/main/java path marker

- **intent:** Discover per-module production source roots via a src/main/java path marker
- **started:** 2026-08-02

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

- **`MutationCandidateGenerator.collectProductionSources(projectRoot)`** — walks the whole tree
  from `projectRoot` with `Files.walkFileTree`, pruning `.git`, `target`, and `build` subtrees in
  `preVisitDirectory`, and collects every `.java` whose repo-relative, forward-slashed path
  contains a `src/main/java/` segment. Discovers one production root *per module* rather than
  assuming a single `src/main/java` at the repo root. · status: documented
- **`MutationCandidateGenerator` sourcePath is now repo-relative** — each emitted
  `MutationCandidate.sourcePath()` includes the module dir
  (`shenyu-common/src/main/java/.../Flag.java`), not a module-relative path. This is the contract
  `HistoricalMutationValidator.modulePathFor` depends on: `ReactorModuleGraph.moduleOf(sourcePath)`
  can only resolve the owning module (for `-pl` scoping) when the path is repo-relative. · status: documented
- **`MutationCandidateGenerator.classNameOf(repoRelativePath)`** — derives the FQN from the path
  *after* the last `src/main/java/` marker (same trick `TestModuleIndex.classNameOf` uses for
  `src/test/java/`), so the class name is correct regardless of which module the file lives in.
  · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Root cause of shenyu `generated: 0` (silent no-op on every real reactor)** — the old code
  resolved a single `projectRoot/src/main/java` and returned `List.of()` when absent. A real
  multi-module reactor has NO `src/main/java` at the repo root — every source lives in a submodule
  — so the generator found zero classes, mutation validation produced zero mutants, and the pair
  reported PASS having tested nothing. A false PASS is the dangerous direction for a shadow-mode
  gate (§III). · status: documented
- **Marker-matching accepts a documented limitation over POM-parsing** — a module that relocates
  its sources via a non-standard `<build><sourceDirectory>` (not `src/main/java`) is skipped.
  Chosen deliberately: the fallback is *fewer* mutants, never a wrong verdict, so it can't produce
  a false PASS; parsing every POM to honor a vanishingly rare case would couple candidate discovery
  to Maven's model when the rest of the class is a pure text scanner. · status: documented
- **`target`/`build` pruning keeps generated sources out of the corpus** — a `.java` under
  `mod/target/generated-sources/` also contains a `src/main/java/` segment only if the generator
  emitted one, but the subtree is pruned in `preVisitDirectory` before any file is visited, so
  build output is never mutated. `src/test/java` sources carry no `src/main/java/` marker and are
  ignored by the same predicate. · status: documented
- **Strict superset of the old single-root behavior** — a repo with exactly one
  `src/main/java` at the root still matches the marker, so every existing single-module test
  passes unchanged; the change only *adds* the ability to find per-module roots. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: match a src/main/java path marker over parsing each module's POM sourceDirectory

- **where:** `blastradius-validator/.../mutation/MutationCandidateGenerator.java`
- **why:** A real reactor has no src/main/java at the repo root; walking the tree and matching a src/main/java/ path segment discovers one production root per module and yields a repo-relative sourcePath, which is exactly what ReactorModuleGraph.moduleOf needs to resolve the owning module for -pl scoping. It reuses the same marker trick TestModuleIndex already uses for src/test/java and is a strict superset of the old single-root behavior.
- **alternative:** Parse each module's pom.xml <build><sourceDirectory> — rejected: needs a POM parser and reactor-model traversal to honor a vanishingly rare non-standard layout, couples candidate discovery to Maven's model when the rest of the class is a pure text scanner, and the marker fallback (skip the odd module) only ever yields fewer mutants, never a false PASS.
- **design:** ../design.md
- **constitution:** §III
- **trust:** ✓ verified
