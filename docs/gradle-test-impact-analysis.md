# Test impact analysis for Gradle pull requests

Blastradius contains a Gradle plugin for selecting tests affected by Java and conventional
Kotlin source changes. The plugin configures Gradle Java `Test` tasks and uses a dependency
index recorded on the base branch. Its source is in
[`blastradius-gradle-plugin`](../blastradius-gradle-plugin).

## Availability

The plugin ID is `io.github.baokhang83.blastradius`. The plugin marker for version `0.3.3`
was not available from the Gradle Plugin Portal when checked on 2026-11-06. The project does
not currently document a published Gradle distribution. Build and integrate the plugin from
source if evaluating it; do not assume the standard `plugins { ... version '0.3.3' }` block
will resolve from the public portal. The Gradle build uses JDK 21 or later.

## Pull request workflow

Once the plugin has been made available to a build and applied, configure its `baseRef`
extension property to a Git reference available in the checkout. Run the ordinary
`./gradlew test` task. A base-branch run records an index under `.blastradius/`; a pull request
run restores that index and uses the Git merge base to determine changes. New and modified
tests and conservative fallback selections are included.

For ephemeral CI runners, persist the index after a successful base-branch build and restore
it for pull requests. Fetch enough Git history for the merge base. The
[GitHub Actions cache example](../README.md#github-actions-cache-example) shows the shared
cache pattern; an S3-compatible index store is another option. A missing usable index causes
a full test run. Run the full suite regularly: see the
[known limitations](../README.md#known-limitations) and
[historical validation](../HISTORICAL_REPLAY_ANALYSIS.md).

The [Maven plugin](maven-junit5-test-impact-analysis.md) is available on Maven Central today.
