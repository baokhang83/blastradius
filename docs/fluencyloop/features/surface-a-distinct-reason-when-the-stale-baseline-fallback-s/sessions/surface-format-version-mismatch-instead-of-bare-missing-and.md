# Session: surface FORMAT_VERSION_MISMATCH instead of bare MISSING, and fix TrackRunner's self-poisoning test isolation bug

- **intent:** surface FORMAT_VERSION_MISMATCH instead of bare MISSING, and fix TrackRunner's self-poisoning test isolation bug
- **started:** 2026-07-27

---

## Knowledge transfer

- **`IndexApplicabilityResolver`'s two-overload shape**: a 2-arg exact-match check and a 5-arg
  enumeration fallback that runs only when the exact key misses. The fallback walks every
  commit-keyed index on disk, filters to ancestor-reachable candidates, and previously reduced
  "found candidates, none qualified" and "found nothing at all" to the same bare `MISSING` —
  now it distinguishes them via `KeyEvaluation(candidate, formatIncompatible)`. Status:
  documented.
- **GitHub Actions `restore-keys` prefix matching is not aware of what's inside the tarball it
  restores**: a `cache-hit` output of `false` combined with a restore-key hit that happens to
  share the *current commit's own sha as a substring of the matched key name* does not mean the
  cache's actual on-disk contents are anchored at that commit — it only means the key *string*
  matched a prefix. If the job that later `save`s under the new key never actually refreshed
  the on-disk files (e.g. a forked subprocess failed before writing), the save step silently
  re-uploads stale data under the new commit's label. Status: documented.
- **`TrackRunner` re-forks `mvn clean test` on the whole reactor with `-DargLine=-javaagent:...`**
  to collect real dependency data — this means every module's own test suite, including
  `blastradius-core`'s tests *for the tracking agent itself*, runs a second time inside a JVM
  that genuinely has the agent attached. Any test that assumes "no agent is attached" as its
  baseline is only safe in the primary (non-TRACK) build; it must actively force that state
  rather than assume it. Status: documented.
- **`MethodHandles.lookup().defineHiddenClass(bytes, false)` self-loading a test class's own
  compiled bytecode is fragile to what's in that bytecode**: adding a lambda (and its
  `invokedynamic` call site) to `DependencyTrackingAgentTest` broke
  `newlyCreatedHiddenClassUsesItsStableSourceName` with a `VerifyError`, because the hidden-class
  redefinition can't cleanly re-link an `invokedynamic` bootstrap bound to the original class's
  identity. Any future edit to this test class must avoid introducing lambdas/method references.
  Status: documented.

## Decision: report FORMAT_VERSION_MISMATCH over bare MISSING when the enumeration fallback's only candidates are format-stale

- **where:** `blastradius-maven-plugin/src/main/java/io/github/baokhang83/blastradius/plugin/index/IndexApplicabilityResolver.java`
- **why:** PR #121/#122 both printed 'no persisted index found (MISSING)' when an index genuinely existed on disk but predated the v1->v2 format bump; the enumeration path silently discarded format-incompatible candidates instead of reporting why, so operators couldn't tell 'nothing was ever cached' from 'a stale-schema index was found and correctly rejected'
- **alternative:** leave enumeration collapsing all rejections to bare MISSING — rejected: hides the real reason from the console/report, violating this repo's Explainability principle, and looks identical to a cold cache to anyone debugging why the full suite ran
- **design:** ../design.md#sequence-enumeration-fallback-picks-a-reason-not-just-a-candidate
- **constitution:** §V
- **trust:** ✓ verified

## Decision: reset the static instrumentation seam via reflection in DependencyTrackingAgentTest instead of assuming the JVM has no agent attached

- **where:** `blastradius-core/src/test/java/io/github/baokhang83/blastradius/core/tracking/DependencyTrackingAgentTest.java`
- **why:** TrackRunner re-forks 'mvn clean test' on the whole reactor with a real -javaagent attached to collect dependency data, which re-runs blastradius-core's own test suite inside that instrumented JVM; two tests assumed the static Instrumentation field was always null (true in a plain unit-test run, false inside TrackRunner's subprocess), so they failed deterministically every time main tried to refresh its index — main could never successfully re-track, so every downstream PR fell back to the full suite with no way to recover on its own
- **alternative:** leave the tests asserting on ambient JVM state — rejected: this is exactly the risk flagged (but deferred) in the ambient-dependency PR's own notes ('the unit-test gap is covered only for the safe no-agent path'); it materialized as a self-inflicted, deterministic TRACK failure, not flakiness. Also rejected: wrapping the reset in a Runnable lambda — a lambda adds an invokedynamic call site to this class's bytecode, which breaks a separate pre-existing test that self-loads this class's own compiled .class bytes as a hidden class; the reset is inlined per-test instead
- **constitution:** §V
- **trust:** ✓ verified
