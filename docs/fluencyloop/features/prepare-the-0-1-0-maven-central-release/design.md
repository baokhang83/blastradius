# Design: prepare the 0.1.0 Maven Central release

started: 2026-07-19

## Release shape

Only the Maven plugin is a public artifact. The core engine is embedded into it, while the
validator remains an internal verification tool. The parent centralizes release controls so the
same release profile produces sources, Javadoc, signatures, a flattened consumer POM, and a
Central Portal bundle.

```mermaid
classDiagram
  class ParentPom {
    +release profile
    +Maven 3.9.6 floor
    +Central metadata
  }
  class Core {
    internal engine
    deploy skipped
  }
  class Validator {
    internal verification tool
    deploy skipped
  }
  class MavenPlugin {
    public artifact
    shaded core, JGit, Jackson
    plugin descriptor and help goal
  }
  class InvokerITs {
    TRACK SELECT FALLBACK
    version-skew isolation
  }
  class GitHubActions {
    build workflow
    tagged release workflow
  }
  class CentralPortal {
    signed release bundle
  }

  ParentPom --> Core
  ParentPom --> Validator
  ParentPom --> MavenPlugin
  MavenPlugin --> Core : shade
  InvokerITs --> MavenPlugin : runs in Maven subprocess
  GitHubActions --> InvokerITs
  GitHubActions --> CentralPortal : v* tag only
  MavenPlugin --> CentralPortal : deploy only artifact
```

## Release flow

```mermaid
sequenceDiagram
  participant Dev as Developer or CI
  participant Maven as Maven reactor
  participant IT as Invoker fixtures
  participant Plugin as Shaded plugin
  participant Central as Central Portal

  Dev->>Maven: mvn verify
  Maven->>IT: execute TRACK, SELECT, FALLBACK, isolation
  IT->>Plugin: resolve installed plugin and run select
  Plugin-->>IT: mode, selected tests, console report
  IT-->>Maven: verified fixture assertions
  Dev->>Maven: mvn -Prelease deploy
  Maven->>Maven: attach sources, Javadoc, signatures, flattened POM
  Maven->>Central: upload signed plugin bundle
  Central-->>Dev: validate and publish release
```

## Key decisions

- The plugin shades the internal core, JGit, and Jackson under private package names. This makes
  the published plugin self-contained and keeps a consumer's versions from leaking into the Mojo.
- Release-only work stays in the `release` profile, so local `test`/`verify` remains quick while
  Central receives its required companion artifacts and signatures.
- The Central publishing extension stages reactor artifacts independently of Maven's normal deploy
  skip flag, so its release configuration explicitly excludes the parent, core, and validator;
  only the shaded plugin enters the public bundle.
- The Invoker executes Maven fixture projects rather than only testing Mojo classes. That is the
  closest repeatable check of the installed plugin's actual lifecycle behavior.
- CI imports its dedicated private signing key and passphrase from encrypted GPG secrets while the
  public fingerprint is distributed through a supported keyserver. Namespace claiming, secret
  creation, tag creation, and observing the completed Central deployment remain account-authorized
  operations.
