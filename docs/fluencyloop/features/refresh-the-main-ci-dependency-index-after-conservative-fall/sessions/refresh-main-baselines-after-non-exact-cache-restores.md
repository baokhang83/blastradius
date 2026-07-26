# Session: Refresh main baselines after non-exact cache restores

- **intent:** Refresh main baselines after non-exact cache restores
- **started:** 2026-07-26

## Knowledge transfer

- **GitHub Actions cache restore:** An exact primary-key hit identifies an archive already
  associated with the checked-out commit. A `restore-keys` match only supplies an older usable
  archive, so its cache key cannot prove that its embedded dependency index was produced by the
  current commit. **Status:** documented.
- **Main baseline refresh:** The verify step owns the CI-specific exact-hit test. On a main exact
  miss it passes `-Dblastradius.mode=track`, which reuses `SelectMojo` and `TrackRunner` to run
  unfiltered tracking and write a dependency index under the checked-out commit key. **Status:**
  documented.
- **Cache persistence invariant:** The cache archive saved under a main SHA must contain an index
  anchored to that SHA. Prefix-restored baseline data may coexist in the archive, but it must not
  be the only index represented by the new key. **Status:** documented.

## Decision: refresh non-exact main cache restores with TRACK

- **where:** `.github/workflows/build.yml verify step`
- **why:** GitHub Actions alone knows whether the restored archive matched the checked-out SHA, so an exact miss on main explicitly invokes the plugin's existing TRACK mode to persist a commit-keyed fresh index before that SHA is cached.
- **alternative:** Teach SelectMojo about GitHub Actions cache provenance — rejected: it would couple the portable Maven plugin to one CI transport instead of passing its existing explicit mode.
- **design:** ../design.md#sequence-refresh-a-main-baseline-after-an-exact-cache-miss
- **constitution:** III
- **trust:** ✓ verified
