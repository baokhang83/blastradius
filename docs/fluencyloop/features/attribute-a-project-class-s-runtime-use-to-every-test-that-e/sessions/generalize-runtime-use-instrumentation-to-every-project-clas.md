# Session: generalize runtime-use instrumentation to every project class's first load

- **intent:** generalize runtime-use instrumentation to every project class's first load
- **started:** 2026-07-28

---

## Knowledge transfer

- **`DependencyTrackingAgent#transform`** — a `ClassFileTransformer` callback the JVM fires exactly once per class-load event. Before this slice it recorded a class only when a JUnit test window was open (`currentTestSupplier.get() != null`); it now *also* runs `AmbientClassInstrumenter` over every project class regardless, so a class's later reuse (not reload) by a different test can still be attributed. Status: documented.
- **Why the bug was invisible before** — Spring's `TestContext` framework caches an `ApplicationContext` by its config signature and reuses it across test classes; a bean like `ConfigsServiceImpl` is only ever instantiated (and class-loaded) once, by whichever test class first needs that context. Every later test reusing the cached context exercises the same instance with no new class-load event, so the old one-shot attribution model silently missed it. Not Spring-specific — applies to any cached/pooled/singleton project resource. Status: documented.
- **`AmbientClassInstrumenter#instrument`** — unchanged in this slice. Injects a static `DependencyTrackingAgent.recordAmbientExecution(name)` call at method entry plus at every field/type/class-literal reference inside the method body, so a class's runtime execution records not just itself but every project class it directly references. Returns `null` (no-op) for an all-abstract/native class. Status: documented.
- **`retransformProjectAmbientClasses`** — the pre-existing pre-first-test snapshot pass. Now guarded to skip (and un-ambient) any class already present in `ambientChecksums`, since the new inline path in `transform()` catches almost everything it used to. It remains as the fallback for classes the instrumenter can't rewrite, and for third-party classes (never instrumented, out of scope for this slice). Status: documented.
- **`CONFIGURED_PROJECT_ROOT`** — the `blastradius.projectRoot` system property is set once, by `TrackRunner`, on the forked JVM's command line before the agent attaches; nothing in the codebase mutates it afterward (verified by grep across the repo). Safe to resolve once as a `static final` field rather than re-resolving (with a real `toRealPath()` filesystem call) on every class load. Status: documented.

## Decision: instrument every project class inline at transform(), not just the pre-first-test snapshot

- **where:** `blastradius-core/.../tracking/DependencyTrackingAgent.java#transform`
- **why:** the JVM calls transform() exactly once per class-load; a cached/singleton instance (e.g. a Spring bean) only ever gets that one call, so only whichever test triggered it was ever attributed — this closes that gap for every project class, not just the narrow pre-first-test set
- **alternative:** leave instrumentation scoped to the pre-first-test ambient snapshot only — rejected: that's exactly the mechanism that produced the observed 0-of-3627 selection on real shenyu
- **design:** ../design.md#class-diagram
- **constitution:** §III
- **trust:** ✓ verified

## Decision: additive-only: keep the old ambient-snapshot/retransform path as a fallback

- **where:** `blastradius-core/.../tracking/DependencyTrackingAgent.java#retransformProjectAmbientClasses`
- **why:** a full unification (retiring the old snapshot+retransform mechanism entirely) is cleaner in the abstract now that it's mostly redundant, but rewriting it risks the many passing tests built around it for a use case that hasn't happened yet
- **alternative:** delete snapshotAmbientDependencies/retransformProjectAmbientClasses and rely solely on the new inline path — rejected: no second concrete need has forced that yet, and it's a much larger, riskier diff
- **design:** ../design.md#class-diagram
- **constitution:** §II
- **trust:** ✓ verified

## Decision: guard retransformProjectAmbientClasses against double-instrumenting classes already caught inline

- **where:** `blastradius-core/.../tracking/DependencyTrackingAgent.java#retransformProjectAmbientClasses`
- **why:** once transform() instruments a project class at its first load, the old pre-first-test snapshot pass would otherwise retransform the same class again, injecting the runtime-use callback twice
- **alternative:** leave the snapshot pass unguarded — rejected: harmless functionally but wastefully double-injects callbacks into every project class discovery-loaded before the first test
- **trust:** ✓ verified

## Decision: cache the resolved project root as a static final field instead of resolving it per class-load

- **where:** `blastradius-core/.../tracking/DependencyTrackingAgent.java#CONFIGURED_PROJECT_ROOT`
- **why:** resolving it touches the filesystem (toRealPath()); that ran once before (pre-first-test snapshot only), but the generalized transform() now runs this check on every class load in the JVM, so a per-call syscall is a real cost. The property is a JVM-startup-fixed -D flag (set once by TrackRunner; grepped the whole repo, nothing mutates it dynamically), so eager caching is safe
- **alternative:** keep resolving System.getProperty + toRealPath() fresh on every transform() call — rejected: real, avoidable per-class-load filesystem cost across the whole JVM
- **constitution:** §VIII
- **trust:** ✓ verified
