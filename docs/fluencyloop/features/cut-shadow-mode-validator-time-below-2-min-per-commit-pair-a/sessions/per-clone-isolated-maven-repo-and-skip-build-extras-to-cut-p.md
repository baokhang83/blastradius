# Session: per-clone isolated maven repo and skip-build-extras to cut per-build time

- **intent:** per-clone isolated maven repo and skip-build-extras to cut per-build time
- **started:** 2026-07-30

---

## Knowledge transfer

_The ground this slice makes understandable — components, roles, and conditions explained,
persisted so the fluency doesn't evaporate with the conversation. About the work, never a person._

### Components (role, conditions)

- **`CommitCheckout.isolatedMavenRepoFor(workDir)`** — static, single-source definition of a clone's private local Maven repo: a `<workDir>-m2` **sibling** of the scratch checkout. Static so the one place that names the `-m2` convention is also the one that cleans it up (`close()`); the Maven runner derives the same path rather than re-encoding the suffix. Returned to `MavenBuildRunner` via `withIsolatedRepo(...)`, which appends `-Dmaven.repo.local=<repo>` to the command. · status: documented
- **`MavenBuildRunner.withIsolatedRepo(command, projectDir)`** — package-visible seam that appends exactly one `-Dmaven.repo.local=...` when the runner was constructed with `isolatedRepo=true`, and returns the command untouched otherwise. Kept separate from `command(...)` so the isolation flag never alters the argument list every existing caller/test asserts. Gated on `buildConcurrency > 1` in `RunCommand` — a serial run has no lock contention to solve, so it keeps the warm shared `~/.m2`. · status: documented
- **`MavenBuildRunner.BUILD_EXTRA_SKIPS`** — the curated set appended when `--skip-build-extras` is set: `-Djacoco.skip`, `-Dcheckstyle.skip`, `-Drat.skip`, `-DskipRemoteResources`. Coverage / lint / license-header / resource-bundling — plugins that run during `clean test` but decide neither which tests run nor whether they pass. Threaded `RunConfig.skipBuildExtras` → `RunCommand` → 4-arg `MavenBuildRunner` constructor → appended at the end of `command(...)` (applies to full runs and single-test confirmFailure reruns alike). · status: documented

### Hard-won conditions (gotchas, root causes, limitations)

- **Per-clone `-m2` is the sweet spot between shared and per-build.** A shared `~/.m2` under K concurrent builds serializes on `maven-remote-resources-plugin`'s file lock — apache/shenyu's documented "Could not acquire lock(s)". A fresh empty repo per *build* removes contention but re-downloads every dependency every time. One repo per *clone* gives each concurrent build its own lock file (no contention) while staying warm across the many commits that clone builds over a run. Proven: 4 cold builds on isolated repos clustered within 23s; 4 equal ~360M `-m2` dirs = no lock contention. · status: documented
- **The `-m2` repo is a sibling, not a child, of the working copy — deliberately.** If it lived inside the checkout, `git checkout` would see it as an untracked dir to scan and §VII's `deleteAllTargetDirectories` walk would descend into it. As a sibling, both skip it entirely. `close()` deletes both the scratch dir and the sibling `-m2`. · status: documented
- **JGit holds memory-mapped locks on `.git` pack files on Windows**, so the scratch clone's own recursive deletion is best-effort and can leave the dir on disk until GC. The isolated `-m2` repo has no such locks, so *its* cleanup is deterministic — which is why the close() test asserts only the `-m2` deletion, not the scratch dir. · status: follow-up
- **Isolated repos do NOT isolate the network namespace.** Shenyu's integration tests bind fixed ports (e.g. 8080); K concurrent builds reaching the same port-binding test can still collide (`BindException`) even with separate `-m2` repos. A real risk of concurrency, orthogonal to the lock-contention this slice solves. · status: documented
- **`--skip-build-extras` is soundness-neutral, unlike scoping.** These flags change neither the test population nor pass/fail, so ground truth (and `WouldMissComparator`'s base/head population match) is untouched — the §III line holds. This is exactly why it is a *curated* set, not a free-form `--maven-args` pass-through: an arbitrary pass-through could carry `-DskipTests` or `-pl` and silently corrupt the ground truth this tool exists to establish. · status: documented
- **Measured, not assumed.** Against apache/shenyu the flag cut a warm full-reactor build from ~24 min to ~13-14 min under 6-way concurrency (~45%). It does NOT reach the ~5 min seen on a single warm slot — that figure was an artifact of five sibling slots being cold (I/O-bound, not competing for CPU). The dominant residual cost is Surefire + Spring/DB startup in `shenyu-admin`, which no skip flag can touch. · status: documented
- **The flag is only safe where the target's pom lets these plugins be skipped by property and none gate the build.** A project that binds rat/enforcer as a hard gate erroring on skip would turn a real build into a spurious failure. Verified clean for shenyu; verify per-target before a full run. · status: documented

---

## Decision: per-clone isolated Maven repo over shared ~/.m2 or per-build repo

- **where:** `blastradius-validator/.../git/CommitCheckout.java (isolatedMavenRepoFor) + MavenBuildRunner.withIsolatedRepo`
- **why:** one -Dmaven.repo.local per clone gives each concurrent build its own lock file (no maven-remote-resources 'Could not acquire lock(s)' contention) while staying warm across the many commits that clone builds
- **alternative:** shared ~/.m2 — rejected: K builds serialize on the plugin's file lock; OR a fresh empty repo per build — rejected: re-downloads every dependency every build
- **constitution:** §III
- **trust:** ✓ verified

## Decision: sibling -m2 path over a child of the working copy

- **where:** `blastradius-validator/.../git/CommitCheckout.java (isolatedMavenRepoFor)`
- **why:** a <workDir>-m2 sibling keeps the repo out of the working tree, so git checkout never scans it as an untracked dir and §VII's deleteAllTargetDirectories walk never descends into it; close() still deletes both
- **alternative:** put the repo inside the checkout (e.g. <workDir>/.m2) — rejected: the checkout walk and target/-cleanup walk would both descend into a multi-hundred-MB repo every commit
- **constitution:** §VII
- **trust:** ✓ verified

## Decision: curated --skip-build-extras flag over a free-form --maven-args pass-through

- **where:** `blastradius-validator/.../build/MavenBuildRunner.java (BUILD_EXTRA_SKIPS) + cli Main/RunConfig/RunCommand`
- **why:** a fixed set of coverage/lint/license/resource skips is soundness-neutral (changes neither test population nor pass/fail, so §III and WouldMissComparator's base/head match hold); it cut shenyu warm builds ~24m to ~13-14m
- **alternative:** a generic --maven-args pass-through — rejected: could carry -DskipTests or -pl and silently corrupt the ground truth this tool exists to establish
- **constitution:** §III
- **trust:** ✓ verified
