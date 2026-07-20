# Session: Route Maven index access through the store SPI

- **intent:** Route Maven index access through the store SPI
- **started:** 2026-07-20

## Decision: Keep Maven applicability storage-agnostic

- **where:** `blastradius-maven-plugin/.../index/IndexApplicabilityResolver.java; mojo/SelectMojo.java`
- **why:** SelectMojo derives the existing root-relative index key, while the resolver asks IndexStore for that key and preserves MISSING or UNREADABLE fallback statuses without knowing whether bytes came from disk or a future remote backend.
- **alternative:** Pass a Path and a concrete JSON reader into the resolver — rejected because it keeps filesystem behavior at the decision point and duplicates the storage seam that remote backends must replace.
- **design:** ../design.md#sequence-read-or-write-an-existing-local-index
- **constitution:** §II, §III, §IV
- **trust:** ✓ verified

## Knowledge transfer

- **Maven key derivation:** `SelectMojo` still validates `indexPath` against the reactor root,
  then derives the same root-relative `.blastradius/index.json` key. Its TRACK path writes through
  the store; SELECT resolves through the same store. **Status:** documented.
- **Applicability boundary:** `IndexApplicabilityResolver` now knows only `IndexStore`, a key,
  and Git reachability. An empty result remains `MISSING`; a storage read failure remains
  `UNREADABLE`; both preserve the existing unfiltered fallback. **Status:** documented.
- **Test migration:** direct Maven reader/writer tests and fixture setup now use the shared
  `FileIndexStore`, proving the existing `DependencyIndex` JSON survives the move. The focused
  Maven tests passed. **Status:** documented.
