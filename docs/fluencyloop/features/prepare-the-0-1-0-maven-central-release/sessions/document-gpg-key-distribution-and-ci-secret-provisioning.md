# Session: document GPG key distribution and CI secret provisioning

- **intent:** document GPG key distribution and CI secret provisioning
- **started:** 2026-07-20

## Knowledge transfer

- **Release signing key:** The public fingerprint is published to a Central-supported keyserver so
  Central can validate `.asc` signatures; the matching private key must remain only in the
  encrypted `MAVEN_GPG_PRIVATE_KEY` GitHub secret. **Status:** documented.
- **Noninteractive signing:** The release workflow imports that key into the ephemeral runner and
  receives `MAVEN_GPG_PASSPHRASE` separately, allowing Maven's loopback pinentry configuration
  to sign without a workstation GPG agent. The passphrase secret still requires one interactive,
  local provisioning step. **Status:** follow-up.
- **CI signing proof:** `workflow_dispatch` runs the same CI key-import and release-signing path
  with `central.skip.publishing=true`; it is safe to trigger without creating a release tag or
  deploying artifacts. **Status:** documented.

## Decision: separate public-key distribution from encrypted CI signing secrets

- **where:** `docs/releasing.md`
- **why:** The supported keyserver exposes the fingerprint for Central verification while GitHub Actions imports the private key and reads its passphrase only from encrypted secrets.
- **alternative:** Commit the signing material or rely on a workstation GPG agent — rejected: either exposes credentials or makes tag releases non-reproducible.
- **design:** ../design.md#release-flow
- **trust:** ⚠ not independently verified

## Decision: add a manual non-publishing signing check instead of using a test tag

- **where:** `.github/workflows/release.yml`
- **why:** workflow_dispatch imports the same encrypted key and runs the release signing phase while central.skip.publishing prevents any deployment.
- **alternative:** Push a temporary v* tag to test signing — rejected: the existing tag trigger would attempt a real Central deployment.
- **design:** ../design.md#release-flow
- **trust:** ⚠ not independently verified
