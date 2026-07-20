# Design: enforce the required JDK 21 build check on main

started: 2026-07-20

## Protection shape

```mermaid
classDiagram
  class PullRequest {
    +head commit
    +merge request
  }
  class BuildWorkflow {
    +Maven 3.9.6
    +JDK 21
    +clean verify
  }
  class StatusCheck {
    +Maven 3.9.6 / JDK 21
    +success or failure
  }
  class MainBranch {
    +main
  }
  class BranchProtection {
    +require status check
    +require current base
    +enforce for admins
  }

  PullRequest --> BuildWorkflow : triggers
  BuildWorkflow --> StatusCheck : reports
  BranchProtection --> StatusCheck : requires
  BranchProtection --> MainBranch : protects
  PullRequest --> MainBranch : merges only when allowed
```

## Merge flow

```mermaid
sequenceDiagram
  participant Dev as Developer
  participant PR as Pull request
  participant CI as Build workflow
  participant Check as Required status check
  participant Main as main protection

  Dev->>PR: push change
  PR->>CI: trigger push and PR builds
  CI->>CI: Maven 3.9.6, JDK 21, clean verify
  CI->>Check: report Maven 3.9.6 / JDK 21
  PR->>Main: request merge
  alt check succeeds and is current
    Main-->>PR: permit merge
  else check fails, is missing, or is stale
    Main-->>PR: block merge
  end
```

## Key decision

Require the existing `Maven 3.9.6 / JDK 21` check with strict freshness and admin
enforcement. This protects the same full-suite build already used by the repository without
introducing a second workflow or weakening the gate for administrators.
