# Session: Reconstruct CI test-selection reliability fixes merged on 2026-07-26

- **intent:** Reconstruct CI test-selection reliability fixes merged on 2026-07-26
- **started:** 2026-07-26
- **sources:** PRs #89, #91, #92, #94, #96, #98, #99, and #102; merged on 2026-07-26

## Decision: Persist an empty baseline for every started test

- **where:** `DependencyTrackingAgent` and `TestBoundaryListener`
- **why:** PR #89 records that tests with no class-load events need an explicit empty baseline so selection does not mistake them for newly added tests.
- **alternative:** Infer absence from dependency records — rejected by the merged implementation because an existing test that loads no new classes is observably different from a new test.
- **design:** `../design.md#class-diagram`
- **constitution:** §§III, V
- **trust:** ✓ confirmed 2026-07-26

## Decision: Treat tracking as an isolated, all-or-nothing transaction

- **where:** `TrackRunner`, `SelectMojo`, and `SurefireFilterApplier`
- **why:** PRs #91 and #92 record that tracking must be restricted to test forks and that a failed tracker must never persist partial data; the ambient build falls back instead.
- **alternative:** Attach through `JAVA_TOOL_OPTIONS` and retain records from a failed child — rejected because the agent leaked into nested Maven processes and a nonzero tracking run can leave an incomplete index.
- **design:** `../design.md#sequence-safe-tracking-and-matrix-feedback`
- **constitution:** §§III, V
- **trust:** ✓ confirmed 2026-07-26

## Decision: Prepare checksum machinery outside class transformation and block re-entrancy

- **where:** `DependencyTrackingAgent` checksum path
- **why:** PRs #94 and #96 record JDK 25 class-loading recursion while computing checksums, addressed with a re-entrancy guard and eager SHA-256 initialization.
- **alternative:** Lazily create a digest during every transformation — rejected because security-provider initialization can load classes and re-enter the transformer, producing `ClassCircularityError`.
- **design:** `../design.md#sequence-safe-tracking-and-matrix-feedback`
- **constitution:** §§III, VI
- **trust:** ✓ confirmed 2026-07-26

## Decision: Cache timing history beside the dependency index for every module

- **where:** `.github/workflows/build.yml` cache paths
- **why:** PR #98 records that timing histories are module-local and must be restored and saved with the shared dependency index for estimates to become complete.
- **alternative:** Cache only the root `.blastradius` directory — rejected because module reports and their timing histories live below each Maven module.
- **design:** `../design.md#class-diagram`
- **constitution:** §§II, V
- **trust:** ✓ confirmed 2026-07-26

## Decision: Aggregate JDK reports in one post-matrix feedback job

- **where:** `.github/workflows/build.yml` selection-feedback job and summary renderer
- **why:** PR #99 records that each JDK publishes its report as an artifact and one post-matrix job owns the single PR comment.
- **alternative:** Let both matrix jobs upsert the same comment — rejected because concurrent writers produce competing or last-writer-wins feedback.
- **design:** `../design.md#sequence-safe-tracking-and-matrix-feedback`
- **constitution:** §§II, V
- **trust:** ✓ confirmed 2026-07-26

## Decision: Normalize Surefire timing identities before calculating savings

- **where:** `BuildReport` timing lookup
- **why:** PR #102 records that Surefire XML includes parameter signatures while discovery uses baseline identities, so durations must be normalized before completeness and savings are calculated.
- **alternative:** Require exact raw method-name equality — rejected because valid parameterized or injected Surefire names would falsely make timing coverage incomplete.
- **design:** `../design.md#class-diagram`
- **constitution:** §§IV, V
- **trust:** ✓ confirmed 2026-07-26

## Knowledge transfer

### Tracking data integrity

- **`TestBoundaryListener` and `DependencyTrackingAgent`:** Each real test start creates a dependency-map entry before the test is published as current. A map with no entries means "existing test with no observed class loads," while no map means "no baseline"; selection needs that distinction to avoid rerunning established tests as if they were new. **Status:** documented.
- **`TrackRunner` and `SelectMojo`:** TRACK is a child Maven build. The agent is placed in Surefire's `argLine`, so Maven infrastructure and nested Maven invocations are not instrumented. Only a zero-exit child produces an index; failure carries a diagnostic output tail and leaves the ambient build in conservative fallback. **Status:** documented.
- **`SurefireFilterApplier`:** An empty chosen set is expressed with Maven's `skipTests`, not an empty `test` property, because Surefire treats the latter as no filter and would run everything. **Status:** documented.

### JDK-25 agent safety

- **`DependencyTrackingAgent.transform`:** The transformer observes class loads only while a test is active. It holds a thread-local re-entrancy guard around checksum computation, so classes loaded by the checksum path cannot recursively transform themselves. **Status:** documented.
- **`DependencyTrackingAgent` SHA-256 state:** SHA-256 is initialized while the agent class loads, before the transformer is registered, and serialized while used because `MessageDigest` is mutable. This avoids security-provider initialization during a JDK class-load callback. **Status:** documented.

### CI persistence and feedback

- **GitHub Actions cache:** Main builds save, and PR builds restore, both the shared root index and each module's `.blastradius` timing history; `target`-local generated data is explicitly excluded. Timing estimates stay absent until all skipped tests have history. **Status:** documented.
- **Matrix feedback:** Each JDK job retains its own job summary and uploads its reports. A post-matrix job downloads complete JDK artifacts and is the only writer of the PR comment, producing one labelled table rather than racing updates. **Status:** documented.
- **`BuildReport`:** Surefire timing identities are folded to the discovery identity and duplicate parameterized invocations are summed. Completeness means every skipped discovered test has a duration under that stable key. **Status:** documented.
