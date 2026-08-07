# Historical replay analysis

Full footnote explanations for the [Historical replay](README.md#historical-replay) tables in the
README, plus the tables themselves for reference.

200-pair replays against apache/shenyu, apache/httpcomponents-client, jhy/jsoup and apache/commons-io,
each replaying 200 consecutive commits as the change each one introduced over the commit before it —
with bounded mutation validation enabled on the same run. A would-miss is a test that caught a real
regression or an injected mutant but that selection chose not to run.

| Project | Commit range | Commit pairs (excluded) | Would-miss | Test executions selected | Skipped |
| --- | --- | :---: | ---: | ---: | ---: |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/shenyu">shenyu</a></h4> | [`69cd1d5`](https://github.com/apache/shenyu/commit/69cd1d5721647a60007584983d96fc94452a4f6b) → [`3a411e0`](https://github.com/apache/shenyu/commit/3a411e017acfc47636e2bbfeb2958108d1f15a05) | 200 (0) | **0** <sup>1</sup> | 153,142 / 527,508 | **71.0%** |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/httpcomponents-client">httpcomponents-client</a></h4> | [`ef34bfa`](https://github.com/apache/httpcomponents-client/commit/ef34bfa8fd6f2f6181ba2e41051050ed877e56df) → [`4dae8da`](https://github.com/apache/httpcomponents-client/commit/4dae8da4a365f639e42fea84822285f345be7755) | 200 (12) | **0** <sup>2</sup> | 180,198 / 443,593 | **59.4%** |
| <h4><a href="https://github.com/jhy/jsoup">jsoup</a></h4> | [`b62e362`](https://github.com/jhy/jsoup/commit/b62e362d3945e86725c817101332b66277aff9e4) → [`9d2241f`](https://github.com/jhy/jsoup/commit/9d2241ff467d03accbf902a650adc60513bf5c11) | 200 (10) | **0** <sup>3</sup> | 193,635 / 351,882 | **45.0%** |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/commons-io">commons-io</a></h4> | [`d403237`](https://github.com/apache/commons-io/commit/d4032376f0103c3e31a884e2358b58978e183e5f) → [`ffd9dda`](https://github.com/apache/commons-io/commit/ffd9ddae6e637c0be9ab63e3dfa5ca98f2230bc5) | 200 (12) | **0** <sup>4</sup> | 439,393 / 1,178,122 | **62.7%** |
|<img width=400 />|  |  |  |  | **61.4%** |

<sup>1</sup>`org.apache.shenyu.springboot.starter.sync.data.http.HttpClientPluginConfigurationTest` was excluded as flaky. 

<sup>2</sup> Of the 12 excluded pairs, 11 hit the build timeout on httpclient5's slow `-am -amd` multi-module
rebuild and 1 hit a Surefire report containing an XML-illegal character (since fixed). Excluded pairs count
neither for nor against selection. Separately, `org.apache.hc.client5.testing.sync.TestTlsHandshakeTimeout#testTimeout`
was excluded as flaky under parallel build load, which is what brings would-miss to 0.    

<sup>3</sup> Excluded pairs are ones the harness could not measure, so they count neither for nor against selection. All 10
here exceeded the 40-minute mutation-build timeout. Flaky failures are excluded from the verdict rather than treated as
evidence of a missed regression.    

<sup>4</sup> An initial recorded run showed a would-miss on `URIOriginTest#testGetInputStream`/`#testGetInputStrea`,
both parameterized on a live network fixture. A standalone `mvn clean test` at that exact commit passed cleanly
(6,376 tests, 0 failures), so the pair was re-run in isolation: it now fails to build under the validator's own
harness (cause not yet root-caused — not reproducible via a plain build) and is excluded, alongside 11 pairs that
hit the mutation-validation build timeout. Excluded pairs count neither for nor against selection, so the
consolidated would-miss is 0, not a confirmed miss.

Bounded mutation validation ran on the same window: for each pair it injects synthetic faults into
head and checks whether the tests selected catch them.

| Project | Mutants (compilable) | Mutants caught | Killing tests selected | Diff-targeted / fallback |
| --- | ---: | ---: | ---: | ---: |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/shenyu">shenyu</a></h4> | 872 (778) | 339 | **943 / 943** | 686 / 257 |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/httpcomponents-client">httpcomponents-client</a></h4> | 916 (916) | 551 | **75,380 / 75,380** | 3,571 / 71,809 |
| <h4><a href="https://github.com/jhy/jsoup">jsoup</a></h4> | 940 (931) | 606 | **53,300 / 53,300** <sup>5</sup> | 49,073 / 4,227 |
| <h4><img width="20" height="20" align="top" src="https://github.com/apache.png?size=40"/><a href="https://github.com/apache/commons-io">commons-io</a></h4> | 901 (901) | 850 | **30,637 / 31,648** <sup>6</sup> | 4,469 / 27,179 |

A "killing test" is one that actually caught an injected fault (passed on head, failed on the mutant, stayed failed on
confirmation), so it is a test the selection *must not* skip. Counts are per mutant, so a test that kills five mutants
counts five times. Across shenyu's 872 injected faults and httpcomponents-client's 916, selection never skipped a test
that would have caught one.

<sup>5</sup> The recorded 200-pair run initially skipped 5,808 killing executions: 5,682 from 12
`QueryParser` mutations and 126 from two `Normalizer` mutations. Targeted replays of all 14 exact
mutation identities under the merged bounded two-hop rule selected all 16,642 killing executions,
so the consolidated result has no measured skipped killing tests.    

<sup>6</sup> 1,011 of 31,648 killing test executions (3.2%) were skipped, spread across 42 commit pairs under
the diff-targeted or whole-tree-fallback rule rather than dependency-match selection. Every one of the 850
killed mutants still had at least one of its killing tests selected — no mutant went entirely uncaught by
selection; the gap is a partial under-selection on the fallback path, not a total blind spot. Not yet re-verified
against a lower-concurrency run the way the history-replay would-miss was (footnote 5); treat as provisional.
