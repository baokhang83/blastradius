# Session: Add a generic root-contained local index store

- **intent:** Add a generic root-contained local index store
- **started:** 2026-07-20

## Decision: Keep a generic two-operation index-store SPI in core

- **where:** `blastradius-core/.../index/IndexStore.java; FileIndexStore.java`
- **why:** Maven and Gradle already duplicate local JSON index I/O, so a generic get/put boundary gives both the same behavior and lets a later remote backend replace storage without changing selection callers.
- **alternative:** Keep Maven- and Gradle-specific file readers/writers, or design a remote-provider API now — rejected because duplication has already appeared while provider configuration is premature until #27.
- **design:** ../design.md#class-diagram
- **constitution:** §II, §III, §IV
- **trust:** ✓ verified

## Knowledge transfer

- **`IndexStore<T>`:** a public, generic two-operation boundary. Callers own the stable key and
  value type; an implementation owns how that key is persisted or retrieved. **Status:** documented.
- **`FileIndexStore<T>`:** serializes a value with Jackson below one normalized project root.
  A missing file returns `Optional.empty()`; malformed JSON remains a read error so callers can
  choose their existing safe fallback. **Status:** documented.
- **Root containment:** every key is normalized below the configured root; blank, absolute, or
  parent-traversing keys fail before I/O. This keeps the existing project-local index rule intact
  even when a future backend gives callers more key choices. **Status:** documented.
- **Validation:** `FileIndexStoreTest` proves missing, nested-directory round-trip, and
  root-escape behavior; the focused core test passed. **Status:** documented.
