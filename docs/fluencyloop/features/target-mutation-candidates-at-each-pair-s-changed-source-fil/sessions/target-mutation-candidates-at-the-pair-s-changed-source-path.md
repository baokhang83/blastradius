# Session: Target mutation candidates at the pair's changed source paths with whole-tree fallback

- **intent:** Target mutation candidates at the pair's changed source paths with whole-tree fallback
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

- **`MutationCandidateGenerator.generate(root, classFilter, pathPool, maxClasses, maxMutations)`** —
  new overload restricting the corpus to production sources whose repo-relative path is in
  `pathPool`. `null` pool = whole tree (the other overloads delegate here with `null`); a non-empty
  pool = set-membership filter applied *before* `classFilter` in the same single walk, so the class
  budget and deterministic ordering are unchanged. An empty pool returns empty — the generator does
  NOT fall back on its own; the caller owns that policy. · status: documented
- **`HistoricalMutationValidator.validate()` diff-targeting + fallback** — classifies the pair's own
  diff (`changedFileClassifier`, already a field), keeps `JAVA_SOURCE` paths under `src/main/java/`
  as the pool, and generates targeted. If the targeted result is empty, re-generates with no pool
  (whole-tree fallback). So a pair mutates the code it changed; a docs/config/test-only pair still
  exercises some mutant instead of silently validating nothing. · status: documented
- **`ChangedFile.path` ⇄ `MutationCandidate.sourcePath` path contract** — both are repo-relative,
  forward-slashed (`shenyu-.../src/main/java/.../Foo.java`), so the pool is a direct set-membership
  test with no translation. This alignment is what makes targeting a two-line filter rather than a
  path-mapping layer. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **The diff was already computed — just never fed to candidate selection** — `validate()`'s
  downstream `analyzeMutant` already called `changedFileClassifier.classify` to drive *selection*
  analysis, but candidate *generation* ran on the whole tree with a null `classFilter`. The two
  steps never intersected, so mutants landed in an arbitrary early-sorted class
  (shenyu: `AbstractPathDataChangedListener`) rather than the changed file. There was no soundness
  reason for this — mutating a changed file is equally sound — it was simply an unextended MVP. · status: documented
- **Fallback lives in the caller, not the generator** — the generator returning empty on an empty
  pool is deliberate: the "still exercise some mutant" policy is the validator's intent (§III), and
  burying it in the generator would make `generate(root, filter, emptySet, ...)` silently mean
  "whole tree", which is a surprising contract. Keeping the generator literal (empty pool → empty)
  and the fallback explicit in `validate()` keeps each honest. · status: documented
- **Verified on real shenyu** — for the head commit (a `TcpBootstrapServer` memory-leak fix),
  targeted generation mutates `TcpBootstrapServer` in `shenyu-protocol-tcp` (a leaf-ish module,
  cheap `-amd`), where whole-tree mutated `AbstractPathDataChangedListener` in
  `shenyu-admin-listener-api` — confirming both the relevance win and the cheaper-fanout
  performance win. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: intersect changed paths inside the generator over looping the single-class classFilter

- **where:** `blastradius-validator/.../mutation/MutationCandidateGenerator.java`
- **why:** One new generate overload takes a Set<String> path pool and filters collectProductionSources against it in the SAME single walk, preserving the maxMutationClasses global budget and deterministic ordering. ChangedFile.path already matches sourcePath's repo-relative form, so it's a plain set-membership test.
- **alternative:** Loop the validator over each changed class calling generate(classFilter=eachClass) N times — rejected: re-walks the whole tree once per changed class (N walks/pair) and cannot honor maxMutationClasses as a budget across the changed set.
- **design:** ../design.md
- **constitution:** §III
- **trust:** ✓ verified
