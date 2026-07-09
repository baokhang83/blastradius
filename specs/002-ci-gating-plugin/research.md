# Phase 0 Research: CI-Gating Maven Plugin

## 1. Splitting "keep the index fresh" from "apply the index" into two mechanisms

**Decision**: The plugin never attaches the dependency-tracking agent to the live,
currently-executing build being gated. Instead, which of two mechanisms applies is decided by
*whose* build this is, not just whether an index happens to be missing:

- **Track**: triggered when the current build is *of the base reference itself* (i.e. the
  currently checked-out commit resolves to the same commit as `baseRef` — the common shape of
  a post-merge/trunk CI build), or when explicitly requested via `-Dblastradius.mode=track`.
  The goal forks the target project's own `mvn test` as an independent subprocess, with the
  tracking agent attached via `JAVA_TOOL_OPTIONS` — reusing the exact mechanism
  `blastradius-core` inherits from the validator (unique-file-per-JVM, merge-on-read,
  `TestIdentity.baselineKey()` normalization) unchanged. This subprocess's result becomes the
  new persisted index. The current (ambient) build's own Surefire execution is left completely
  untouched — no filter applied, runs exactly as it would with the plugin absent.
- **Select**: triggered on any other build (a PR/feature branch) once a valid index exists —
  the goal computes the diff between the base reference and the current changes, runs
  `blastradius-core`'s proven `SelectionEngine` against it, and hands the resulting test names
  to Surefire/Failsafe via the standard `-Dtest=` (or `<test>`) parameter — a plain
  test-selection filter, not a JVM-instrumentation mechanism.
- **No valid index and this build is *not* of the base reference** (FR-007's cold-start/stale
  case, e.g. a PR build before the very first trunk track run has ever happened): the ambient
  build runs its full, unfiltered suite — same safe behavior as any other fallback — but the
  goal does **not** additionally fork a track subprocess for this commit. A branch/PR commit is
  a poor anchor for the shared index anyway (it's transient, not the reference other builds
  will diff against), so there is nothing to gain from paying that cost here; the next trunk
  build is what establishes or refreshes the real index.

**Why not "fork a track subprocess whenever the index is merely missing"**: an earlier version
of this decision would have forked the tracking subprocess on *any* build lacking a valid
index, including ordinary PR builds. That would silently double the wall-clock cost of exactly
the builds this feature exists to speed up — the PR's own full run, plus a redundant full run
in the subprocess, every time the index was cold. Anchoring "track" to base-ref builds instead
means the expensive index-refresh cost is paid by trunk/post-merge CI (typically less
latency-sensitive than a PR feedback loop) and paid once per refresh, not once per PR build
sharing that same stale index.

**Rationale**: `-Dtest=` and `argLine` are unrelated Surefire mechanisms. `-Dtest=` is a
well-established, always-honored test filter — the validator's own `MavenBuildRunner.
runSingleTest` already relies on it for flaky-failure confirmation re-runs, and it never
exhibited any of the fragility `argLine` did. So "select" mode carries no risk of repeating
research.md #2 of the validator's own spec. "Track" mode is the one that needs the javaagent,
and doing it as a separate subprocess — rather than trying to inject the agent into the
*live* build's own Surefire fork — sidesteps the entire class of bug already found and fixed
once (a literal `<argLine>` value, or a plugin like JaCoCo overwriting the `argLine` property):
those bugs exist precisely because something *else* in the target project's own build also
wants to control `argLine`. A subprocess `mvn test` invoked and controlled entirely by the
plugin (identical in spirit to the validator's own `MavenBuildRunner`) has that same property
available to set unopposed, with nothing else in that specific invocation racing for it.

**Alternatives considered**:
- *Attach the agent to the live, gated build's own Surefire fork directly* (e.g. by having the
  Mojo set the `argLine` project property in an early phase). Rejected: this is exactly the
  mechanism that failed against real projects during T061 — an adopting team's own JaCoCo usage
  (extremely common) or a hardcoded `<argLine>` would silently defeat it, and unlike the
  validator (which invokes an external, uncontrolled project via subprocess and can freely
  choose its own invocation flags), a Mojo running inside the live build has no clean way to
  guarantee it "wins" a property that plugin ordering and the adopting project's own pom.xml
  also influence.
- *Always track and select in the same run* (i.e., every build re-derives the full dependency
  index from scratch, every time). Rejected: this is what SC-005 explicitly rules out — it
  would mean paying full-suite cost on every build, defeating the entire point of the feature.
- *A separate `blastradius:track` goal, wired up explicitly by the adopting team in their CI
  config for base-ref builds only*. A reasonable alternative, and mechanically identical to
  the chosen design's internal "track" path — rejected only as the *primary* interface, in
  favor of one `blastradius:select` goal whose mode is decided automatically (by comparing the
  current commit to the resolved `baseRef`, or via an explicit `-Dblastradius.mode=track`
  escape hatch for a team that wants to force a refresh), because that keeps adoption to "one
  opt-in build step" (spec.md SC-003) rather than requiring two goals wired into two different
  CI trigger conditions. The two paths are the same code either way, just selected differently.

## 2. Where the persisted dependency index lives

**Decision**: A single JSON file under the target project's own working directory (e.g.
`.blastradius/index.json`), read and written directly by the plugin using the same Jackson-based
approach the validator already uses for its reports. Not committed to version control (the
target project's own `.gitignore` is expected to exclude it, same convention as `target/`).

**Rationale**: This keeps the plugin's storage model exactly as simple as the validator's own
(local filesystem, no database, no hosted service — spec.md Assumptions). It also means the
plugin needs zero infrastructure beyond what any Maven build already has. Persisting this file
across ephemeral CI runners (so a PR build can see the index a prior base-ref build produced) is
then just an ordinary CI build-cache problem — restore-by-key-on-base-ref-commit, save on
successful track runs — identical in shape to how teams already cache `~/.m2` or Gradle's build
cache, and squarely the adopting team's own CI configuration, not this plugin's concern.

**Alternatives considered**:
- *A hosted index service the plugin talks to*. Rejected outright — explicitly out of scope
  per spec.md Assumptions ("not a hosted or SaaS service"), and would reintroduce exactly the
  kind of infrastructure dependency Constitution Principle II asks this project to avoid until
  a second real need demonstrates it's necessary.
- *Embedding the index inside `target/`*. Rejected: `target/` is conventionally wiped by `mvn
  clean`, which every build in this project's own lifecycle already relies on (research.md #2
  of the validator's spec: `clean` is required, not cosmetic) — an index that disappears every
  time `clean` runs would defeat its own purpose.

## 3. Deciding whether a persisted index is still applicable

**Decision**: The index records the exact commit (or ref) of the base reference it was built
against. Before "select" mode uses it, the plugin resolves the *current* base reference to a
commit and compares: if they match, the index is applicable as-is; if the base reference has
moved (e.g. new commits landed on the base branch since the index was built), the plugin
computes the diff from the index's *recorded* base commit forward, same as it always does — the
base reference having advanced doesn't itself invalidate the index, since the index describes
dependencies as of a specific commit, not "the base branch" as an abstract moving target. The
index is only treated as inapplicable (triggering fallback per FR-007) when it's missing,
unreadable/corrupt, or references a commit no longer reachable in the project's git history
(e.g. after a history rewrite).

**Rationale**: Treating "the base branch moved" as automatic invalidation would force a track
run far more often than necessary, undermining SC-005. Anchoring to a specific commit (not a
moving branch name) is what makes an index usable across many PR builds without needing a fresh
track run for each one, as long as the recorded commit is still a real, reachable ancestor.

**Alternatives considered**:
- *Time-based expiry (e.g., "index invalid after 24 hours")*. Rejected: arbitrary and disconnected
  from what actually makes an index wrong — a stale-but-still-reachable index from three days ago
  is exactly as valid as one from three minutes ago, as long as the diff is still computed
  correctly from its recorded anchor.

## Summary of decisions carried forward unchanged from the validator (`blastradius-core`)

| Topic | Decision |
|---|---|
| Dependency tracking mechanism | Dynamic, class-level, `java.lang.instrument` agent + checksums (validator research.md #1) |
| Agent attachment (track mode only) | `JAVA_TOOL_OPTIONS`, not `-DargLine` (validator research.md #2, revised) |
| Multi-fork data collection | Unique file per JVM, merged on read (validator research.md #2 addendum, T061 fix) |
| Parameterized-test baseline matching | `TestIdentity.baselineKey()` normalization (T061 fix) |
| Fallback rule | Any non-Java-source change → select everything (validator FR-006) |
| New/modified test rule | Always selected regardless of baseline (validator FR-007) |
