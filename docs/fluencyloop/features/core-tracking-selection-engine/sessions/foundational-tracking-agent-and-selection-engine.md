# Session: foundational tracking agent and selection engine

- **intent:** foundational tracking agent and selection engine
- **started:** 2026-07-12

> ✓ **Backfilled, then reviewed.** `blastradius-core` shipped in the initial commit (`9f00dca`)
> without real-time teaching. These decisions were reconstructed from the code, the validator's
> Phase-0 ADR (`specs/001-shadow-mode-validator/research.md`), and the constitution
> (`.specify/memory/constitution.md`), then **confirmed by the maintainer on 2026-07-12** — all
> four upgraded from `⚠` to `✓`. Principle numbers reference the current constitution v2.1.0.

---

## Knowledge transfer

_The ground this backfill makes understandable — the components, roles, and conditions of the
engine. About the work, not any person._

- **DependencyTrackingAgent (`ClassFileTransformer`)** — observes every class load in the JVM
  it's attached to and records a SHA-256 of the raw bytecode; never modifies bytecode. Runs as a
  `-javaagent` for the life of a test JVM. · status: documented
- **TestBoundaryListener (JUnit 5 `TestExecutionListener` + `ThreadLocal`)** — marks the
  currently-executing test so each class load is attributed to it; assumes sequential,
  one-thread-per-test execution (no parallelism). · status: documented
- **SelectionEngine + `FallbackSelector` / `NewOrModifiedTestSelector` / `DependencyMatchSelector`
  + `SelectionReason`** — compose one explained `SelectionDecision` per test; precedence: fallback
  short-circuits all, else new/modified beats dependency match; every outcome carries a named
  reason. · status: documented
- **ChangedFileClassifier + `FileKind` fallback rule** — classify each changed file; a
  `NON_SOURCE` change (unobservable to class-load tracking) selects every test, while
  `JAVA_SOURCE` is matched by dependency. Shipped binary; `INERT` added later. · status: documented
- **`blastradius-core` module boundary** — the shared engine consumed by both the validator and
  the Maven plugin; extracted only once the second consumer existed. · status: documented

---

## Decision: dynamic per-test class-load tracking via a java.lang.instrument agent

- **where:** `blastradius-core/.../tracking/DependencyTrackingAgent.java`, `tracking/TestBoundaryListener.java`
- **why:** the engine needs "which classes did this test load" plus "did that class's bytecode change" — not line coverage. A `ClassFileTransformer` observes every class load (recording a SHA-256 of the raw bytecode, never modifying it), and a JUnit 5 `TestExecutionListener` marks the currently-executing test via a `ThreadLocal` so each load is attributed to the test that triggered it. A purpose-built, dependency-free tracker stays small and auditable, and only dynamic tracking sees reflective / DI / classpath-scanning edges. (research.md §1)
- **alternative:** (a) reuse JaCoCo's runtime agent and derive touched-classes from coverage data — rejected: it is built for line/branch coverage, and resetting its `RuntimeData` per test adds real complexity for a capability we don't need (only "touched or not" + a checksum). (b) static bytecode analysis only (STARTS-style) — rejected outright: cannot observe reflection/DI edges, which is the exact soundness gap the tool exists to close.
- **constitution:** §VI (modern, non-abandoned foundations — standard agent instrumentation), §II (no more machinery than the problem needs). *Note: research.md cites this as “Principle VII” — that predates constitution v2.1.0’s renumbering; §VI confirmed on review.*
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-12)

---

## Decision: selection as three composable selectors, each carrying an explicit reason

- **where:** `blastradius-core/.../selection/SelectionEngine.java` (+ `DependencyMatchSelector`, `FallbackSelector`, `NewOrModifiedTestSelector`, `SelectionReason`)
- **why:** `SelectionEngine` composes three single-purpose selectors and every `SelectionDecision` carries a `SelectionReason` enum (`DEPENDENCY_MATCH`, `FALLBACK_NON_SOURCE_CHANGE`, `NEW_OR_MODIFIED_TEST`, `NO_MATCH`). Each test's outcome is traceable to a concrete, named cause, and the precedence (fallback short-circuits all; else new/modified beats ordinary dependency match) is visible in one place.
- **alternative:** one monolithic selection method returning a bare boolean per test — rejected: an opaque skip decision with no surfaced reason cannot be audited or challenged, which is exactly what the tool's credibility can't afford.
- **constitution:** §V (Explainability — every decision traceable to a human-readable reason), §II (small single-purpose classes).
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-12)

---

## Decision: FileKind-driven conservative fallback (sound-by-default)

- **where:** `blastradius-core/.../git/FileKind.java`, `git/ChangedFileClassifier.java`, `selection/FallbackSelector.java`
- **why:** changed files are classified by kind. A `JAVA_SOURCE` change is matched precisely against each test's tracked dependencies; anything the class-load tracker cannot soundly observe (`NON_SOURCE` — resources, `pom.xml`/dependency changes, migrations, build config) triggers `FallbackSelector.shouldFallback`, which selects **every** test. An unnecessary full run is a far cheaper error than a missed failure, so ambiguity defaults to running more.
- **alternative:** treat all changes uniformly through dependency matching — rejected: it would silently skip tests affected by changes the tracker can't see (reflectively-loaded resources, dependency bumps), trading away soundness for speed.
- **constitution:** §III (Safety Over Speed — conservative fallback for unobservable changes).
- **note:** shipped as a **binary** classifier (`JAVA_SOURCE` vs `NON_SOURCE`). The third `INERT` kind — provably-inert files select zero tests — was a **later** refinement (constitution v2.1.0 Sync Impact Report; the `inert-file-classification` feature), not part of this original slice.
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-12)

---

## Decision: extract blastradius-core as a shared module only once there was a second consumer

- **where:** root reactor `pom.xml` (`blastradius-core` module); consumed by `blastradius-validator` and `blastradius-maven-plugin`
- **why:** the tracking + selection logic was first built for the shadow-mode **validator**. It was extracted into a standalone `blastradius-core` module only when the **Maven plugin** became a second real consumer of the same engine — an abstraction justified by a demonstrated second use, not a guessed-at future one.
- **alternative:** design a shared `core` module up front in anticipation of the plugin — rejected: speculative generality before a second concrete use guesses at the shared shape and adds machinery the problem doesn't yet need.
- **constitution:** §II (Clean Code & Simplicity — extract an abstraction only once a second real use demonstrates the shared shape).
- **trust:** ✓ verified — maintainer-confirmed on backfill review (2026-07-12)
