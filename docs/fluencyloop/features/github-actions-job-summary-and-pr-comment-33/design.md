# Design: GitHub Actions job summary and PR comment (#33)

started: 2026-07-26

## Class diagram

```mermaid
classDiagram
  class BuildWorkflow {
    +mvn clean verify
    +publish selection summary
  }
  class SelectionSummaryAction {
    +report-path input
    +comment input = true
    +github-token input
  }
  class SummaryRenderer {
    +read report JSON
    +write Markdown
  }
  class GitHubJobSummary {
    +GITHUB_STEP_SUMMARY
  }
  class PullRequestCommenter {
    +upsert marked comment
  }
  class BuildReport {
    +selectedCount
    +totalCount
    +skippedCount
    +estimatedTimeSavedMillis
    +reasonCounts
  }
  BuildWorkflow --> SelectionSummaryAction : invokes after build
  SelectionSummaryAction --> SummaryRenderer : parses
  SummaryRenderer --> BuildReport : reads
  SummaryRenderer --> GitHubJobSummary : appends Markdown
  SelectionSummaryAction --> PullRequestCommenter : internal PRs only
  PullRequestCommenter --> BuildReport : summarizes
```

## Sequence: publish CI feedback without affecting verification

```mermaid
sequenceDiagram
  participant B as Build workflow
  participant A as Selection summary action
  participant R as last-build-report.json
  participant J as GITHUB_STEP_SUMMARY
  participant G as GitHub API

  B->>B: run Maven verification
  B->>A: invoke after build
  A->>R: read selection report
  alt report exists
    A->>J: append Markdown summary
    alt internal pull request and comment enabled
      A->>G: create or update marked PR comment
      G-->>A: comment result
    else forked pull request or comment disabled
      A->>A: skip PR comment
    end
  else report missing
    A->>J: state that no report was produced
  end
  A-->>B: never change verification result
```

## Rationale

The publisher is a composite GitHub Action rather than Maven-plugin code. Maven
produces a portable local report; GitHub Actions owns the event context, token,
job-summary surface, and pull-request permission boundary. This makes the
integration reusable by every workflow that runs `blastradius:select`.

The action always writes a job-summary entry, including when no report was
produced. PR comments are enabled by default for same-repository pull requests,
where the workflow grants `pull-requests: write`. They are intentionally skipped
for forks and are non-fatal, so feedback can never change the build result.

One stable HTML marker identifies the comment to update. This preserves a single
current result per pull request instead of adding a comment on every CI run.
