# Session: Detect Kotlin inline modifier variants conservatively

- **intent:** Detect Kotlin inline modifier variants conservatively
- **started:** 2026-07-20

## Knowledge transfer

- **Kotlin modifier order** — `inline` can be followed by another function modifier, including
  `suspend`, before `fun`. The fallback detector therefore recognizes an inline modifier sequence
  rather than only the adjacent phrase `inline fun`. **Status:** documented.
- **Conservative source scan** — the classifier reads both old and new Kotlin blobs from Git and
  uses a deliberately broad text pattern. A false positive merely preserves the full suite;
  avoiding an inline false negative is the safety-critical outcome. **Status:** documented.

## Decision: Use a conservative inline-modifier detector instead of parsing Kotlin

- **where:** `ChangedFileClassifier Kotlin inline fallback`
- **why:** Kotlin permits modifier order such as inline suspend fun; treating a potential inline declaration as non-source avoids a false-negative selection even when a text scan is broader than necessary.
- **alternative:** Parse Kotlin syntax or match only inline fun — rejected: a parser adds a compiler-language dependency, while the narrow text pattern missed valid modifier order.
- **design:** ../design.md#design
- **constitution:** §III
- **trust:** ✓ verified
