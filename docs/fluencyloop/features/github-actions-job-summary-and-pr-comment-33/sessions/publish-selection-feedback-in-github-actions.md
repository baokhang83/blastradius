# Session: Publish selection feedback in GitHub Actions

- **intent:** Publish selection feedback in GitHub Actions
- **started:** 2026-07-26

## Decision: publish feedback from a composite action

- **where:** `.github/actions/blastradius-selection-summary`
- **why:** GitHub Actions owns event context, tokens, summary rendering, and pull-request permissions while Maven keeps producing a portable local report.
- **alternative:** Call the GitHub API from the Maven plugin — rejected because it couples the plugin to GitHub credentials and cannot safely reason about forked pull-request permissions.
- **design:** `../design.md#rationale`
- **trust:** ⚠ not independently verified

## Decision: validate report data before publishing it

- **where:** `renderer validation`
- **why:** Validate report counts and timing estimates before rendering so malformed or incomplete local JSON cannot publish misleading CI savings; the action will warn and write a neutral summary instead of failing verification.
- **alternative:** Trust every report as the current schema — rejected because the action must be safe for reports from older plugin versions and interrupted writes.
- **design:** `../design.md#rationale`
- **trust:** ⚠ not independently verified
