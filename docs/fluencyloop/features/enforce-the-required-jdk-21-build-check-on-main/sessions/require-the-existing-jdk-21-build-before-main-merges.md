# Session: require the existing JDK 21 build before main merges

- **intent:** require the existing JDK 21 build before main merges
- **started:** 2026-07-20

## Knowledge transfer

- **Required status check:** GitHub matches the check by both the displayed name and its GitHub
  Actions app identity. `strict: true` makes a result stale when the target branch moves, forcing
  a rerun before merge. **Status:** documented.
- **Administrative enforcement:** `enforce_admins: true` applies the same required check to
  repository administrators; force-pushes and branch deletion remain disabled. **Status:**
  documented.

## Decision: require the current JDK 21 build before main merges

- **where:** `GitHub repository settings: main branch protection`
- **why:** The existing full-suite Maven 3.9.6 / JDK 21 check is required with strict freshness and administrator enforcement, so missing, failed, or stale results block a merge.
- **alternative:** Keep CI advisory or permit administrator bypasses — rejected: a failing check would not reliably protect main.
- **design:** ../design.md#merge-flow
- **constitution:** §III
- **trust:** ✓ verified
