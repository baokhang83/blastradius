# Blastradius maturity assessment

**Assessment date:** 2026-08-01  
**Scope:** the public Blastradius repository and its published Maven plugin  
**Framework:** [Apache Project Maturity Model v1.4](https://community.apache.org/apache-way/apache-project-maturity-model.html)

## Purpose and interpretation

This is a self-assessment, not a claim that Blastradius is an Apache Software Foundation
project or that it meets ASF policy. The Apache model is useful outside the ASF, but it
does not define maturity levels: a mature Apache project satisfies every applicable item.

The status labels below are evidence-based:

| Status | Meaning |
| --- | --- |
| **Met** | The repository contains clear, current evidence for the intent of the item. |
| **Partial** | Some evidence exists, but an important policy, process, or verification step is missing. |
| **Gap** | The practice is not currently documented or demonstrated. |
| **Not assessed** | The result needs operational data that is not available in the repository. |
| **ASF-specific** | This depends on ASF governance or ownership and cannot be met by a non-ASF project. |

## Current snapshot

- Public source, build instructions, test-impact evidence, and user documentation are in the
  [README](README.md).
- The Maven reactor enforces Java 21 and Maven 3.9.6+, and CI runs the full verification
  workflow on JDK 21 and JDK 25 ([`pom.xml`](pom.xml),
  [build workflow](.github/workflows/build.yml)).
- The project is licensed under Apache License 2.0 ([`LICENSE`](LICENSE)) and publishes the
  Maven plugin through a documented, signed release process
  ([release guide](docs/releasing.md), [release workflow](.github/workflows/release.yml)).
- The repository currently has no `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`,
  governance document, public maintainer list, or documented voting process.

## Assessment

### Code

| ID | Status | Evidence and next step |
| --- | --- | --- |
| CD10 | **Met** | The Maven plugin is publicly distributed at no charge; the README links to Maven Central. |
| CD20 | **Met** | Source is publicly accessible on GitHub and linked from the README and POM. |
| CD30 | **Met** | A standard Maven build is documented as `mvn clean install`; supported Java and Maven versions are enforced in the parent POM and exercised in CI. |
| CD40 | **Met** | Git history and version tags (`v0.1.0`, `v0.3.0`, `v0.3.1`) allow released source revisions to be recovered. |
| CD50 | **Partial** | GitHub provides commit attribution, but the project has no documented contributor provenance policy (for example, DCO, CLA, or signed-commit expectation). |

### Licenses and copyright

| ID | Status | Evidence and next step |
| --- | --- | --- |
| LC10 | **Met** | [`LICENSE`](LICENSE) and Maven publication metadata declare Apache License 2.0. |
| LC20 | **Partial** | Dependencies are centrally version-managed, but there is no documented dependency-license review or automated license check. |
| LC30 | **Partial** | Dependencies are consumed as open-source Maven artifacts, but this is not verified by a checked-in license inventory or SBOM. |
| LC40 | **Gap** | Apache ICLAs are ASF-specific; for this project, document an appropriate inbound-contribution policy instead. |
| LC50 | **Partial** | The repository has an Apache 2.0 license, but it does not document copyright ownership, third-party notices, or contributor licensing terms. |

### Releases

| ID | Status | Evidence and next step |
| --- | --- | --- |
| RE10 | **Partial** | The release profile attaches source JARs and publishes to Maven Central. Add an explicit source-distribution/archive policy and verify it for each release. |
| RE20 | **ASF-specific** | Blastradius has no ASF PMC. A project-specific release-approval policy is not yet documented. |
| RE30 | **Partial** | The release profile and workflow sign artifacts with GPG; add a documented verification record or automated check for every published release. |
| RE40 | **Met** | The project does not present separate convenience binaries as official releases; Maven artifacts are the documented distribution. |
| RE50 | **Met** | [`docs/releasing.md`](docs/releasing.md) documents prerequisites, tag-based publishing, signing, verification, and a manual fallback. |

### Quality

| ID | Status | Evidence and next step |
| --- | --- | --- |
| QU10 | **Met** | The README documents limitations and the recommended full-suite safety net, and publishes validator evidence rather than making an unconditional safety claim. |
| QU20 | **Partial** | CI and dependency updates exist, but there is no documented secure-development or vulnerability-management policy. |
| QU30 | **Gap** | Add `SECURITY.md` with a private reporting channel, disclosure expectations, and response process. |
| QU40 | **Partial** | [`CHANGELOG.md`](CHANGELOG.md) documents notable changes and compatibility requirements, but there is no stated compatibility/deprecation policy and the current 0.3.1 release is not yet represented. |
| QU50 | **Not assessed** | GitHub Issues is configured in the POM, but the repository does not define or report a bug-response expectation. |

### Community

| ID | Status | Evidence and next step |
| --- | --- | --- |
| CO10 | **Partial** | The README covers adoption and the release guide covers publishing, but the project lacks a single path to contribution, security, governance, and support information. |
| CO20 | **Gap** | Add contribution and code-of-conduct guidance that explicitly welcomes good-faith contributors. |
| CO30 | **Partial** | GitHub Issues and pull requests accept several contribution types, but accepted contribution paths are not documented. |
| CO40 | **Gap** | No meritocratic maintainer or committer model is documented. |
| CO50 | **Gap** | No contributor ladder describes how someone earns review, commit, or decision rights. |
| CO60 | **Gap** | No consensus-based decision process is documented. |
| CO70 | **Not assessed** | There is no documented support channel or response-time measure. |

### Consensus building

| ID | Status | Evidence and next step |
| --- | --- | --- |
| CS10 | **Gap** | No public list identifies contributors with decision authority. |
| CS20 | **Gap** | No public, documented consensus process governs project decisions. |
| CS30 | **Gap** | No voting rules are documented. |
| CS40 | **Gap** | No technical-veto policy is documented. |
| CS50 | **Gap** | No main written decision channel or policy for recording material decisions is documented. |

### Independence

| ID | Status | Evidence and next step |
| --- | --- | --- |
| IN10 | **Gap** | Independence is not yet demonstrable: governance and decision authority are not documented, and the repository history is currently concentrated in the project owner's accounts. Build a maintainer group and publish decision records. |
| IN20 | **Gap** | No policy asks contributors or maintainers to act as individuals rather than organizational representatives. |

### Trademark and branding

| ID | Status | Evidence and next step |
| --- | --- | --- |
| TB10 | **ASF-specific** | Blastradius is not an Apache project and must not imply Apache affiliation. |
| TB20 | **ASF-specific** | The project is not required to use an `apache.org` domain. |
| TB30 | **ASF-specific** | The ASF does not own the Blastradius name or branding. Document project-owned trademark policy if the brand becomes material. |
| TB40 | **ASF-specific** | ASF Brand Management does not apply. Define a maintainer contact for misuse reports if needed. |

## Priority roadmap

1. **Open contribution and security:** add `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`,
   `SECURITY.md`, and a `SUPPORT.md` or equivalent issue template.
2. **Make governance explicit:** publish maintainers and decision rights; document written
   consensus, votes, technical vetoes, and how contributors gain responsibility.
3. **Close licensing/provenance gaps:** define inbound licensing (DCO or CLA), add dependency
   license scanning/SBOM generation, and document `NOTICE` and copyright handling.
4. **Harden release assurance:** verify signed release artifacts in CI, publish a source archive
   policy, and keep the changelog complete for every release.
5. **Measure responsiveness:** choose public support and issue-triage expectations, then review
   them periodically.

## Reassessment

Revisit this document after the first external maintainer is added, after the first security
process exercise, and at each minor release. Evidence should be updated with links to the
adopted policies and their observed use.
