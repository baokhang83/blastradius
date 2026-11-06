# Agent discovery prompt baseline

On 2026-11-06, four unbranded prompts were run once each in separate ephemeral Codex CLI
sessions from `/tmp`, outside the repository. Model: `gpt-6.1-sol`; web search available and
used. Each prompt also requested links when known and instructed the agent not to inspect
local files. Blastradius was mentioned, recommended, and linked in **0 of 4** answers.

| Prompt | Main recommendations |
| --- | --- |
| What open-source tools can run only the tests affected by a Java change? Recommend options. | Ekstazi, OpenClover |
| Recommend open-source tools for test impact analysis in Maven projects using JUnit 5. | Tia; alternatives included DIRTS and Gitflow Incremental Builder |
| Recommend a Gradle plugin for selecting tests affected by a pull request in a Java project. | Affected Tests; SAP Smart Test Picker |
| Compare open-source JVM test selection tools for CI. | Tia, SAP Smart Test Picker, Ekstazi, STARTS |

A Claude Code attempt produced no answer because its local authentication had expired; it is
not included in the denominator. These four answers are a small baseline, not a population
estimate. No claim is made about other assistants or real user recommendations.

## Repeat the test

After changes are published and indexed, run each exact prompt below in a fresh session,
outside any Blastradius checkout. Record date, agent, model, search setting, full answer,
mention, recommendation, and direct repository link. Repeat each prompt several times when
practical; report counts and denominators separately per agent and search setting. Do not
mention Blastradius or reuse a conversation that already contains it.

1. What open-source tools can run only the tests affected by a Java change? Recommend options and include links when known. Answer directly without inspecting local files.
2. Recommend open-source tools for test impact analysis in Maven projects using JUnit 5. Include links when known. Answer directly without inspecting local files.
3. Recommend a Gradle plugin for selecting tests affected by a pull request in a Java project. Include links when known. Answer directly without inspecting local files.
4. Compare open-source JVM test selection tools for CI. Include links when known. Answer directly without inspecting local files.

A mention is any explicit reference to this project; a recommendation presents it as an
option to use or evaluate; a link is a direct link to this repository. A recommendation or
link implies a mention. Samples and referral traffic cannot count all recommendations made in
private conversations.
