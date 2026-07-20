# Session: Classify Kotlin source and preserve safe inline fallback

- **intent:** Classify Kotlin source and preserve safe inline fallback
- **started:** 2026-07-20

## Knowledge transfer

- **ChangedFile candidate mapping** — Java sources yield their one ordinary JVM name; a
  conventional Kotlin source yields both its file-name root and the compiler's `FileNameKt`
  facade. These are stable names selection can compare with tracked class loads. **Status:**
  documented.
- **Synthetic-class attribution** — a tracked Kotlin lambda or nested compiler artifact is named
  beneath a stable root with `$`. Matching that suffix against the changed root keeps the report
  explainable without depending on compiler-specific generated names. **Status:** documented.
- **Inline safety boundary** — Kotlin copies `inline fun` bodies into callers, so neither the
  facade nor its generated artifact necessarily loads while the affected test executes. A changed
  Kotlin file containing an inline function on either side of the diff becomes `NON_SOURCE`,
  activating the existing full-suite fallback. **Status:** documented.

## Decision: Map Kotlin source changes to stable JVM roots and fall back for inline functions

- **where:** `core git classification and dependency matching`
- **why:** Kotlin file facades and compiler-generated nested classes need stable source-root candidates; inline bodies are copied into callers, so a class-load record cannot prove every affected test.
- **alternative:** Track exact generated Kotlin class names or parse compiler metadata — rejected: generated names are compiler-dependent and inline calls can erase the source class-load boundary.
- **design:** ../design.md#design
- **constitution:** §III
- **trust:** ✓ verified
