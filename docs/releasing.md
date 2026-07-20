# Releasing Blastradius

This repository publishes only:

```text
io.github.baokhang83.blastradius:blastradius-maven-plugin:0.1.0
```

`blastradius-core` and `blastradius-validator` are internal reactor modules. The plugin
contains the core and its tracking agent in the published, shaded artifact.

## One-time Maven Central setup

1. Sign in to the [Sonatype Central Portal](https://central.sonatype.com/) with the
   `baokhang83` GitHub account and claim the `io.github.baokhang83` namespace.
2. Create a release signing key and publish its public key to a [supported public OpenPGP
   key server](https://central.sonatype.org/publish/requirements/gpg/). The current release
   key is `5A9A88359BF76360F642B87226369AF45B8CBB75` and is available from the
   [Ubuntu keyserver](https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x5A9A88359BF76360F642B87226369AF45B8CBB75).

   ```bash
   gpg --keyserver hkps://keyserver.ubuntu.com --send-keys \
     5A9A88359BF76360F642B87226369AF45B8CBB75
   ```
3. Add these GitHub Actions secrets:

   | Secret | Value |
   | --- | --- |
   | `CENTRAL_TOKEN_USERNAME` | Central Portal user token username |
   | `CENTRAL_TOKEN_PASSWORD` | Central Portal user token password |
   | `MAVEN_GPG_PRIVATE_KEY` | Base64-encoded ASCII-armored private signing key export |
   | `MAVEN_GPG_PASSPHRASE` | Passphrase for that signing key |

The release workflow writes the Central credentials as the Maven server named `central`,
imports the signing key, and invokes the `release` profile.

Use **Actions -> Release to Maven Central -> Run workflow** to exercise the same CI key import
and signing path without publishing. Only a pushed `v*` tag can run the deploy step.

Set the signing secrets from a terminal with access to the key. `gpg` prompts through the
pinentry program; the passphrase is neither echoed nor stored in shell history.

```bash
export GPG_TTY="$(tty)"
gpg --armor --export-secret-keys 5A9A88359BF76360F642B87226369AF45B8CBB75 \
  | base64 \
  | gh secret set MAVEN_GPG_PRIVATE_KEY --repo baokhang83/blastradius

read -rs 'MAVEN_GPG_PASSPHRASE?GPG passphrase: '
printf '\n'
printf '%s' "$MAVEN_GPG_PASSPHRASE" \
  | gh secret set MAVEN_GPG_PASSPHRASE --repo baokhang83/blastradius
unset MAVEN_GPG_PASSPHRASE
```

## Release procedure

1. Verify a clean candidate locally:

   ```bash
   mvn -B --no-transfer-progress -Prelease -Dgpg.skip=true clean verify
   ```

2. Ensure the release commit is on `main`, its version is `0.1.0`, and the changelog is
   final.
3. Create and push the annotated `v0.1.0` tag. The tag workflow verifies that its commit
   is reachable from `main`, then runs `mvn -Prelease clean deploy`.
4. Wait for Central Portal to report the deployment as published, then verify from a clean
   directory:

   ```bash
   mvn -B --no-transfer-progress \
     io.github.baokhang83.blastradius:blastradius-maven-plugin:0.1.0:help
   ```

5. Copy the plugin configuration in the root [README](../README.md) into a small Git
   project and run `mvn test` once on its base reference. Confirm a `TRACK` report is
   written and the plugin resolves only from Central, not a local reactor install.

For a manual release outside GitHub Actions, configure the same `central` server in
`~/.m2/settings.xml`, export `MAVEN_GPG_PASSPHRASE`, and run `mvn -Prelease clean deploy`.
