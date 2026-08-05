# Session: Core mechanism: synthetic container identity, stacked frames, union into member tests

- **intent:** Core mechanism: synthetic container identity, stacked frames, union into member tests
- **started:** 2026-08-05

## Knowledge transfer

- **`TestIdentity`'s `methodName == null` "class-level identity"** — already documented in the
  record's own Javadoc but unused until this slice; now the container's synthetic identity
  reuses it directly, so no new identity type was needed. *status: documented*
- **`recordAmbientSnapshot()` moving from first-test-start to first-container-start** — a side
  effect of calling it in the container branch of `executionStarted` too. This isn't cosmetic:
  under the old per-test-only trigger, everything loaded during the very first class's
  `@BeforeAll` would already be sitting in the JVM by the time the ambient snapshot ran (at the
  first test), so it would get swept into the permanent, unattributable `ambientDependencies`
  set before this feature's own container-identity attribution ever got a chance to run.
  Triggering the snapshot at container start closes that ordering hole — it now fires strictly
  before `@BeforeAll` runs, so `@BeforeAll`'s loads stay live for `transform()` to attribute.
  *status: documented*
- **`checksumsByTest` for the container key only ever contains `@BeforeAll`'s loads** — not
  because of anything explicit, but as a consequence of `currentTest()` being overwritten the
  moment the first test starts (existing behavior, unchanged). The container-scoped hidden-class
  window-close in `ContainerFrame.beforeAllWindowClosed` exists only because hidden classes don't
  get this boundary for free the way named classes do. *status: documented*
- **`unionContainerDependencies` always removes the container's own `checksumsByTest` entry** —
  deliberate: the container identity is never a real test, so leaving its entry behind would put
  a phantom `(className, null)` key in `recordedDependencies()` that no ground-truth lookup would
  ever match — dead weight in the selection index rather than a bug, but worth removing anyway.
  *status: documented*
- **Known limitations is duplicated, not shared** — `README.md` and
  `blastradius-maven-plugin/README.md` (the artifact actually published to Central) each carry
  their own copy of the limitations list; both needed the `@BeforeAll`→`@AfterAll` edit by hand.
  No templating between them today. *status: follow-up* (worth a follow-up if this drifts again).

---

## Decision: Close the @BeforeAll hidden-class window at the first child test, not at container finish

- **where:** `blastradius-core/.../TestBoundaryListener.java (ContainerFrame.beforeAllWindowClosed)`
- **why:** the named-class map for the container key naturally spans only @BeforeAll since currentTest() moves to the first test the moment it starts; the hidden-class diff has no such natural boundary and has to be told where it closes, or it would span the whole class and leak test-body hidden/lambda classes into sibling tests via the later union
- **alternative:** diff hidden classes at container executionFinished instead — simpler (one fewer branch), but over-attributes every test body's hidden classes to all siblings; safe (extra deps only widen re-runs, never hide a needed one) but noisy
- **design:** ../design.md#decisions
- **trust:** ⚠ not independently verified

## Decision: Stack container frames per thread instead of a single field

- **where:** `blastradius-core/.../TestBoundaryListener.java (CONTAINER_STACK, ContainerFrame)`
- **why:** @Nested classes fire their own container start/finish nested inside the outer one; a stack makes a test join its immediate container's memberTests for free, with no special-casing for nesting depth
- **alternative:** a single container field — simpler, but a @Nested class's start would clobber the outer class's still-open frame, corrupting the outer's memberTests and losing its classesAtStart baseline
- **design:** ../design.md#decisions
- **trust:** ⚠ not independently verified

## Decision: Scoped to @BeforeAll only; @AfterAll stays a documented, separate gap

- **where:** `blastradius-core/.../TestBoundaryListener.java (executionFinished container branch)`
- **why:** TestExecutionListener gives no hook between the last child test finishing and executionFinished(container) (which fires after @AfterAll runs), so closing @AfterAll too would require re-arming currentTest = container after every test finishes, not just before the first — confirmed with the developer to stay scoped to #219 as filed
- **alternative:** make the container a standing/background identity for the whole class lifecycle now — closes @AfterAll and inter-test @BeforeEach/@AfterEach gaps too, but the union at container-finish would attribute those to every sibling test, not just the adjacent one: coarser than this feature's @BeforeAll-only precision. Filed separately as #221 instead
- **design:** ../design.md#decisions
- **trust:** ✓ verified
