# Historical replay analysis

Full footnote explanations for the [Historical replay](README.md#historical-replay) tables in the
README, plus the tables themselves for reference.

200-pair replays against apache/shenyu, apache/httpcomponents-client and jhy/jsoup, each replaying
200 consecutive commits as the change each one introduced over the commit before it — with bounded
mutation validation enabled on the same run. A would-miss is a test that caught a real regression
or an injected mutant but that selection chose not to run.

| Project | Commit range | Commit pairs (excluded) | Would-miss | Test executions selected | Skipped |
| --- | --- | :---: | ---: | ---: | ---: |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/shenyu">shenyu</a></h4> | [`69cd1d5`](https://github.com/apache/shenyu/commit/69cd1d5721647a60007584983d96fc94452a4f6b) → [`3a411e0`](https://github.com/apache/shenyu/commit/3a411e017acfc47636e2bbfeb2958108d1f15a05) | 200 (0) | **0**<sup>1</sup> | 153,142 / 527,508 | **71.0%** |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/httpcomponents-client">httpcomponents-client</a></h4> | [`ef34bfa`](https://github.com/apache/httpcomponents-client/commit/ef34bfa8fd6f2f6181ba2e41051050ed877e56df) → [`4dae8da`](https://github.com/apache/httpcomponents-client/commit/4dae8da4a365f639e42fea84822285f345be7755) | 200 (12) | **0**<sup>2</sup> | 180,198 / 443,593 | **59.4%** |
| <h4><a href="https://github.com/jhy/jsoup">jsoup</a></h4> | [`b62e362`](https://github.com/jhy/jsoup/commit/b62e362d3945e86725c817101332b66277aff9e4) → [`9d2241f`](https://github.com/jhy/jsoup/commit/9d2241ff467d03accbf902a650adc60513bf5c11) | 200 (10) | **0**<sup>3</sup> | 193,635 / 351,882 | **45.0%** |
|<img width=400 />|  |  |  |  | **58.5%** |

<sup>1</sup>`org.apache.shenyu.springboot.starter.sync.data.http.HttpClientPluginConfigurationTest` was excluded as flaky.    
<sup>2</sup> Of the 12 excluded pairs, 11 hit the build timeout on httpclient5's slow `-am -amd` multi-module
rebuild and 1 hit a Surefire report containing an XML-illegal character (since fixed). Excluded pairs count
neither for nor against selection. Separately, `org.apache.hc.client5.testing.sync.TestTlsHandshakeTimeout#testTimeout`
was excluded as flaky under parallel build load, which is what brings would-miss to 0.    
<sup>3</sup> Excluded pairs are ones the harness could not measure, so they count neither for nor against selection. All 10
here hit the 10-minute build timeout: a handful of injected mutants turn jsoup's tree traversal into an infinite loop, and
the harness stops waiting rather than hang the run. The run also saw 35 flaky failures (34 of them
`org.jsoup.parser.HtmlParserTest#handlesManyChildren`); a test that fails once and passes on confirmation rerun is not
evidence of a missed regression, so those count as flaky rather than as would-misses.    

Bounded mutation validation ran on the same window: for each pair it injects synthetic faults into
head and checks whether the tests selected catch them.

| Project | Mutants (compilable) | Mutants caught | Killing tests selected | Diff-targeted / fallback |
| --- | ---: | ---: | :---: | ---: |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/shenyu">shenyu</a></h4> | 872 (778) | 339 | **943 / 943** | 686 / 257 |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/httpcomponents-client">httpcomponents-client</a></h4> | 916 (916) | 551 | **75,380 / 75,380** | 3,571 / 71,809 |
| <h4><a href="https://github.com/jhy/jsoup">jsoup</a></h4> | 942 (938) | 652 | **44,798 / 47,866**<sup>4</sup> | 42,440 / 5,426 |

A "killing test" is one that actually caught an injected fault (passed on head, failed on the mutant, stayed failed on
confirmation), so it is a test the selection *must not* skip. Counts are per mutant, so a test that kills five mutants
counts five times. Across shenyu's 872 injected faults and httpcomponents-client's 916, selection never skipped a test
that would have caught one.

<sup>4</sup> **The selection rules behaved correctly here; the tracking data they were given was incomplete.** Selection skipped 3,068 of jsoup's 47,866 killing executions (6.4%) — 750 distinct tests, since each test is counted
once per mutant it would have caught. The skips are concentrated, not spread: of the 652 mutants that any test caught,
20 had a killing test skipped, and four mutants in `org.jsoup.select.QueryParser` cause 2,980 of the 3,068 by each
skipping the same 745 tests. Selection runs a test when the agent recorded that test loading a changed class. So the check is whether the skipped
tests had recorded loading `QueryParser`. For those four mutants, the answer splits cleanly: of the 665 killing tests
selection ran, 635 had recorded a `QueryParser` load (the remaining 30 had no records at all); of the 745 it skipped,
**not one had**. Every skip follows the rule — no recorded dependency, no reason to run — and every test with the
recorded dependency was run. Given its inputs, selection made no wrong call. The inputs were wrong because jsoup takes its query as a string, `select("div > p")`, and caches the parse behind it, so the agent never attributes the `QueryParser` load to the test that caused it. The dependency is real but invisible to
class-load tracking. `@BeforeAll` hides loads the same way (see [Known limitations](README.md#known-limitations)) and explains
the remaining 88 executions. This is a limit of what tracking can observe, and the daily full-suite run is what covers
it.
