# Session: Self-host the Maven reactor in CI

- **intent:** Self-host the Maven reactor in CI
- **started:** 2026-07-26

## Knowledge transfer

- **Bootstrap coordinate:** CI packages the checked-out plugin, copies it to a private
  `0.1.0-selfhost` coordinate, and changes that copy's plugin descriptor to the same coordinate.
  Maven validates descriptor and requested versions, so relabelling only the repository artifact
  is not enough. Status: documented.
- **Reactor tracking:** the profile is inherited by modules but skips the aggregator POM. The
  first test-bearing module starts the agent-backed track subprocess; later module executions
  reuse its shared index while still writing their own reports. Status: documented.
- **Self-hosted discovery:** the plugin shades Blastradius classes. During self-hosting, project
  test discovery loads the project's Blastradius package before the shaded plugin copy, avoiding
  duplicate-class linkage failures. Status: documented.
- **CI feedback:** the composite action combines explicit module report paths after the reactor,
  then writes the job summary and updates the same-repository pull-request comment. Status:
  documented.


## Decision: bootstrap and coordinate self-hosted selection

- **where:** `root Maven profile and CI workflow`
- **why:** Bootstrap the plugin from the current checkout, run one TRACK subprocess per Maven session, and aggregate module reports so CI uses the reviewed code without multiplying the full reactor run.
- **alternative:** Bind the plugin independently in every module without coordination
- **design:** ../design.md#rationale
- **trust:** ⚠ not independently verified

## Decision: bootstrap the plugin under a temporary reactor-safe revision

- **where:** `root POM version properties and CI bootstrap workflow`
- **why:** Maven rejects a plugin whose coordinate matches a reactor module, and it verifies the plugin descriptor version; building the current checkout at 0.1.0-selfhost gives the bootstrap artifact a valid descriptor while the verification reactor stays at 0.1.0.
- **alternative:** Install the normal plugin coordinate or relabel its JAR — rejected: the former creates a reactor cycle and the latter fails Maven's descriptor-version validation.
- **design:** ../design.md#rationale
- **trust:** ⚠ not independently verified

## Decision: combine existing module reports in the CI summary action

- **where:** `selection-summary action and build workflow`
- **why:** Each Maven module already emits a local report. Combining explicit module paths after the reactor completes produces one accurate CI and pull-request summary without adding Maven lifecycle listener coordination solely for presentation.
- **alternative:** Write an aggregate report from a Maven session listener — rejected: it couples CI presentation to lifecycle internals despite the needed module data already existing.
- **design:** ../design.md#rationale
- **trust:** ✓ verified

## Decision: skip aggregator-only projects before selector mode routing

- **where:** `SelectMojo execution entry point`
- **why:** The root POM participates in the inherited profile but has no test engine. Skipping packaging pom lets the first test-bearing child claim the one reactor-wide TRACK process and prevents an empty parent from failing CI.
- **alternative:** Allow the aggregator to run TRACK — rejected: it attempts to create a JUnit launcher with no engine and stops the reactor before any test module runs.
- **design:** ../design.md#rationale
- **trust:** ✓ verified

## Decision: prefer self-hosted project classes during test discovery

- **where:** `TestDiscoverer class loader`
- **why:** A Maven plugin normally delegates to its shaded classes first. During self-hosting, the project contains the same Blastradius packages, so discovery loads that project namespace child-first and avoids linking tests against the plugin's copies.
- **alternative:** Use ordinary parent-first URLClassLoader delegation — rejected: the S3 test implementation linked to the shaded interface and failed JUnit discovery.
- **design:** ../design.md#rationale
- **trust:** ✓ verified

## Decision: relabel only the copied bootstrap plugin descriptor

- **where:** `CI bootstrap workflow`
- **why:** Maven requires the plugin descriptor version to match the external bootstrap coordinate, but propagating a revision property into parent POMs leaves standalone consumers unresolved. Updating the copied JAR descriptor creates a valid self-host coordinate without changing published metadata.
- **alternative:** Use a reactor-wide Maven revision property — rejected: installed module POMs retained an unresolved parent revision when consumed outside the reactor.
- **design:** ../design.md#rationale
- **trust:** ✓ verified
