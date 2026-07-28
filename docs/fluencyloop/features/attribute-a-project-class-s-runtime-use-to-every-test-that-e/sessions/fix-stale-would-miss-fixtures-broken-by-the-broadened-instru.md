# Session: fix stale would-miss fixtures broken by the broadened instrumentation

- **intent:** fix stale would-miss fixtures broken by the broadened instrumentation
- **started:** 2026-07-28

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

## Decision: <chose X over Y>

- **where:** `<path/to/File.ext>`
- **why:** <the one-line why, engaged with — not post-hoc narration>
- **alternative:** <the rejected option> — rejected: <why>
- **trust:** ⚠ not independently verified

## Knowledge transfer

- **Why these two tests broke** — both fixtures relied on `Shared.value()` being called once during `@BeforeAll` (untracked, no current test) and then called *again* inside the `@Test` body, expecting that second call to be inert (already loaded, `transform()` never refires). That assumption held under the old narrow instrumentation but not the new one: `AmbientClassInstrumenter` injects its `recordAmbientExecution` callback at *every call* to an instrumented method, not just the load event, so the second call re-fires it — correctly, since the test genuinely does depend on `Shared`. Status: documented.
- **What "untracked" now actually requires** — for a dependency to stay genuinely untracked under the new instrumentation, it must be invoked *only* while no test is current (i.e. only from `@BeforeAll`/`@BeforeEach` container callbacks), with the `@Test` body reading a cached result rather than re-invoking the dependency. This is a narrower, more honest characterization of the "documented limitation" than before. Status: documented.

## Decision: fix the would-miss fixtures instead of the production code

- **where:** `blastradius-validator/.../EndToEndVerdictIntegrationTest.java, MultiWouldMissIntegrationTest.java`
- **why:** both fixtures called the shared class a second time from inside the @Test body — under the old narrow instrumentation that second call was inert, but the new inline transform() path instruments every project class at first load, so the injected runtime-use callback re-fires on that second call and correctly attributes the dependency to the test. The fix closes this exact gap too, not just the cached-Spring-bean case; the fixtures no longer construct a genuinely untracked dependency, so they were updated to cache the @BeforeAll result into a static field and never call the shared class again from the @Test body
- **alternative:** treat it as a production bug and special-case @BeforeAll-triggered loads to skip instrumentation — rejected: there is no reliable signal in transform() that distinguishes a @BeforeAll-triggered load from any other no-test-running load, and the broader attribution is strictly more correct
- **design:** ../design.md#sequence-diagram
- **trust:** ✓ verified
