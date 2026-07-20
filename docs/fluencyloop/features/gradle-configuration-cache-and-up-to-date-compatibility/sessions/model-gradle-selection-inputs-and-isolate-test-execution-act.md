# Session: model Gradle selection inputs and isolate Test execution actions

- **intent:** model Gradle selection inputs and isolate Test execution actions
- **started:** 2026-07-20

## Knowledge transfer

- `GitBuildStateSource` is a `ValueSource` whose immutable result is a configuration-cache
  input. Gradle obtains it on every invocation to check the cache entry; unchanged commit IDs
  reuse the configured task graph, while a changed commit produces a new graph deliberately.
- Feature-build `Test` tasks declare base reference, base commit, head commit and the optional
  dependency index as inputs. These declarations cover the data that changes the dynamically
  applied filter, so an unchanged selection can be up-to-date or restored from Gradle's build
  cache without replaying the filter action.
- TRACK configures the agent JVM argument during configuration and uses small `Action<Task>`
  objects for record preparation and index writing. They capture only files and strings; JSON
  readers/writers are created inside `execute`, avoiding the non-serializable Jackson state that
  originally prevented configuration-cache storage.
- Baseline TRACK disables Test output reuse because it must start worker JVMs to generate fresh
  dependency records. This is an intentional boundary: non-baseline SELECT retains normal
  Gradle work avoidance.

## Decision: Model Git state and index as explicit Test inputs

- **where:** `BlastradiusPlugin, GitBuildStateSource, and Test execution actions`
- **why:** Git state and the index determine selection, so Gradle must fingerprint them; task actions retain only serializable values and create runtime helpers during execution, allowing configuration-cache reuse without stale or unsound test results.
- **alternative:** Keep Project-backed actions and undeclared dynamic selection — rejected because configuration-cache serialization fails and cache keys can omit values that change the selected tests.
- **design:** ../design.md#boundaries-and-decisions
- **constitution:** §III, §IV
- **trust:** ✓ verified
