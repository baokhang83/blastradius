# Session: reactor-module-graph-from-the-git-tree

- **intent:** reactor module graph from the git tree
- **started:** 2026-07-29

---

## Knowledge transfer

_The ground this slice makes understandable — components, roles, and conditions explained,
persisted so the fluency doesn't evaporate with the conversation. About the work, never a person._

### Components (role, conditions)

- **`ModuleId`** — value identity for one reactor module: `(artifactId, relativePath)`. The `relativePath` is what attributes a changed file or a test source to the module (path containment); the `artifactId` is what inter-module dependency edges reference. Repo-relative, forward-slashed, no trailing slash; `""` denotes a module rooted at the repo root. · status: documented
- **`ReactorModuleGraph`** — the immutable inter-module graph queried during selection. Three operations: `moduleOf(path)` returns the deepest owning module (or empty), `dependentsOf(module)` returns the module plus its transitive reverse-dependency closure (the full set a change could affect), `isReactorWide(path)` is true for the root aggregator pom or any file attributable to no leaf module. One graph per commit pair. · status: documented
- **`ReactorModuleGraphBuilder.fromRepoTree(repoRoot)`** — constructs the graph purely from disk: walks the tree for `pom.xml`, parses coordinates/dependencies/parent, builds reverse edges. Works identically for the Maven plugin (working tree) and the validator (checked-out scratch copy) because it never consults Maven's runtime model. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Git-tree parsing, not Maven runtime** — the shadow-mode validator has no live `MavenProject`, so the graph MUST be derived from POM files on disk. This is the load-bearing constraint of the whole feature; a runtime-reactor approach would compile but be unusable in the validator. · status: documented
- **`<parent>` counts as a dependency edge** — a child inheriting from a parent POM is affected by changes to that parent's coordinates/config, so parent artifactId refs are treated as edges alongside `<dependency>`. Missing this would under-scope the fallback and silently skip tests (violates §III soundness). · status: documented
- **Deepest-match wins** — `moduleOf` sorts modules by `relativePath` length descending so a file under `moduleA/...` attributes to `moduleA`, never to the reactor-root aggregator that also "contains" it by prefix. · status: documented
- **Unattributable path ⇒ reactor-wide** — `isReactorWide` is true when `moduleOf` is empty, never a narrower guess. A file under no known module is treated as affecting everything; narrowing on uncertainty would be unsound. · status: documented
- **XML parser hardened + build-output skipped** — `DocumentBuilderFactory` uses `FEATURE_SECURE_PROCESSING` and disables external-DTD loading; the tree walk skips `target/`/`build/` so stale generated POMs don't pollute the graph. · status: documented

---

## Decision: derive the reactor graph from the git tree, not Maven's runtime reactor

- **where:** `blastradius-core/.../core/reactor/ReactorModuleGraphBuilder.java`
- **why:** the shadow-mode validator runs against a checked-out scratch copy with no live MavenProject, so the graph must be reconstructable from POM files on disk; this also makes the Maven plugin and validator share one code path
- **alternative:** read Maven's runtime reactor (MavenProject.getProjectReferences) — rejected: unavailable in the validator, which is the primary consumer
- **design:** ../design.md
- **constitution:** §III
- **trust:** ✓ verified

## Decision: treat <parent> refs as dependency edges and default unattributable paths to reactor-wide

- **where:** `blastradius-core/.../core/reactor/ReactorModuleGraph.java`
- **why:** soundness: a child is affected by its parent POM, so parent edges must be in the closure; and any path we cannot attribute to a leaf module must be treated as affecting everything, never guessed narrower — a missed edge silently skips tests that should run
- **alternative:** only follow <dependency> edges and drop unrecognized paths — rejected: both under-scope the fallback and reintroduce the exact false-skip §III forbids
- **design:** ../design.md
- **constitution:** §III
- **trust:** ✓ verified
