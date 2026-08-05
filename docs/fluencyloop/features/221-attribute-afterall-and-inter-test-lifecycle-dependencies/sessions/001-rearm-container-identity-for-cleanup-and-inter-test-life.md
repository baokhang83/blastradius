# Session: rearm container identity for cleanup and inter-test lifecycle dependencies

- **intent:** rearm container identity for cleanup and inter-test lifecycle dependencies
- **started:** 2026-08-05

## Knowledge transfer

- **`TestBoundaryListener` container window:** Class lifecycle code runs outside any individual
  test method, so the listener restores the innermost synthetic container identity after every
  child test. Ordinary class loads in `@AfterAll` and between tests are then recorded under that
  container and unioned into every direct member at container finish. status: documented
- **Per-test callback precision:** The JUnit listener's test execution window includes ordinary
  `@BeforeEach` and `@AfterEach` callbacks, so they retain the method identity that was already
  active before this change. The new container window is for the genuinely unowned interval after
  a test finishes and for class cleanup, not a replacement for per-test attribution. status:
  documented
- **Hidden-class accounting:** Hidden and lambda classes cannot be attributed at transform time;
  they are captured by comparing loaded classes with a per-window baseline. The mutable baseline
  is refreshed when a container window closes or re-arms, keeping test-body hidden classes out of
  sibling baselines while still covering cleanup. status: documented
- **Nested container restoration:** An inner class flushes its parent before it starts. Once the
  inner frame is unioned and removed, the listener restores the parent identity with a fresh
  baseline, so nested test execution does not leak into the outer class while later outer cleanup
  remains attributable. status: documented

## Decision: re-arm the container across every non-test lifecycle interval

- **where:** `blastradius-core/src/main/java/io/github/baokhang83/blastradius/core/tracking/TestBoundaryListener.java`
- **why:** JUnit exposes no cleanup-start boundary, so the container is restored after every child test and its final union conservatively selects every direct member for cleanup or inter-test dependencies.
- **alternative:** Keep only the @BeforeAll window — rejected: @AfterAll-only dependencies remain unowned and can produce a false negative.
- **design:** ../design.md#decision
- **constitution:** §III
- **trust:** ✓ verified
