# Session: Windows mvn launcher fix

- **intent:** Windows mvn launcher fix
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

- **`MavenLauncher` (blastradius-core, `process` package)** — the single OS-aware resolver for the command token every `mvn` fork in the codebase hands to `ProcessBuilder`. `launcherName()` returns `mvn.cmd` on Windows and `mvn` elsewhere; `resolve()` returns an absolute path into `MAVEN_HOME`/`M2_HOME`'s `bin/` when one is set and the launcher file actually exists there, else the bare `launcherName()`. Lives in core because both downstream modules already depend on core. · status: documented
- **Two production call sites** — `MavenBuildRunner.command(...)` (validator) builds the argv list starting with `MavenLauncher.resolve()`; `TrackRunner.track(...)` (maven-plugin) does the same for its `clean test` fork. Both previously hardcoded the literal `"mvn"`. · status: documented
- **Tests pin argv[0] to `MavenLauncher.resolve()`, not `"mvn"`** — `MavenBuildRunnerTest`'s five `command()`-composition assertions validate the *flags* (`-B`, `clean`, `-Dtest=`, `-pl/-am`, `-T`), so they must use the same resolver the production code does rather than re-asserting a literal launcher token that is wrong on Windows. A dedicated `MavenLauncherTest` (3 tests) covers the launcher logic itself. · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **`CreateProcess error=2` on Windows was a bare-name resolution bug, not a missing Maven** — Windows has no `mvn` executable; Maven ships `mvn.cmd`. Java's `ProcessBuilder` only appends `.exe` (never `.cmd`/`.bat`) when resolving a bare argv[0] against `PATH`, so a literal `"mvn"` can never be found even with Maven fully installed and on `PATH`. Naming `mvn.cmd` explicitly — or an absolute path to it — is what makes the fork start. This is why ~21 validator tests that fork a real `mvn` were failing on this box; all 110 now pass. · status: documented
- **Absolute-path-first resolution order is deliberate** — some CI/dev setups export `MAVEN_HOME` but don't put its `bin/` on `PATH`. Preferring the absolute path when the env var points at a real install works there; falling back to the bare OS-correct name works when it doesn't. The fallback is never the naked `"mvn"` on Windows — it's `mvn.cmd`. · status: documented

---

<!-- Decisions are appended below by `fluencyloop decision`. For reference, a block looks like:
## Decision: chose X over Y
- **where:** `path/to/File.ext`
- **why:** the one-line why, engaged with — not post-hoc narration
- **alternative:** the rejected option — rejected: why
- **trust:** ⚠ not independently verified
-->

## Decision: put the OS-aware Maven launcher resolver in blastradius-core, not per call site

- **where:** `blastradius-core/.../process/MavenLauncher.java`
- **why:** Both the validator (MavenBuildRunner) and the maven-plugin (TrackRunner) fork mvn with ProcessBuilder, and both broke identically on Windows (literal "mvn" -> CreateProcess error=2, because Java only appends .exe to bare PATH names and Maven ships mvn.cmd). Both modules already depend on core, so a single MavenLauncher.resolve() there is reachable from both and the fix lives in exactly one place.
- **alternative:** Inline the mvn.cmd check at each of the two call sites — rejected: duplicates the OS branch and the MAVEN_HOME lookup, and the next process fork would silently reintroduce the bug.
- **trust:** ✓ verified

## Decision: prefer an absolute MAVEN_HOME/M2_HOME path, fall back to the bare OS launcher name

- **where:** `blastradius-core/.../process/MavenLauncher.java (resolve)`
- **why:** An absolute path into a real Maven install never depends on PATH at all, so it works even where mvn.cmd isn't on PATH; when no env var points at a valid install, degrading to the bare OS-correct name (mvn.cmd on Windows, mvn elsewhere) lets the OS resolve it via PATH — still never the naked "mvn" that fails on Windows.
- **alternative:** Always use the bare launcher name and trust PATH — rejected: some CI/dev setups set MAVEN_HOME but don't put its bin on PATH, so PATH-only resolution would still fail there.
- **trust:** ✓ verified
