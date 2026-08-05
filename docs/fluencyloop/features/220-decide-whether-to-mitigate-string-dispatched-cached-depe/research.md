# ADR: bounded fallback for cached string-dispatch dependencies

- Status: decided
- Issue: [#220](https://github.com/baokhang83/blastradius/issues/220)
- Follow-up: [#225](https://github.com/baokhang83/blastradius/issues/225)
- Date: 2026-08-05

## Context and evidence

The core index records dynamic class dependencies. A test that calls a string-based API can
depend on a parser without executing that parser when a prior parse result is cached. The dynamic
record therefore contains no `QueryParser` edge even though changing `QueryParser` can change the
test's result.

The [historical replay analysis](../../../../HISTORICAL_REPLAY_ANALYSIS.md) measured the exposure
in jsoup: 3,068 of 47,866 killing executions were skipped, representing 750 distinct tests. Four
`org.jsoup.select.QueryParser` mutants account for 2,980 skips across the same 745 tests. Of 665
killing tests selected for those four mutants, 635 recorded `QueryParser`; none of the 745 skipped
tests did. This is incomplete dynamic input, rather than a failure in the selection rule.

The cache behavior is a plausible cause: jsoup's
[QueryParser documentation](https://jsoup.org/apidocs/org/jsoup/select/QueryParser.html) describes
parsing a CSS query into an evaluator and reusing the result. A cache hit can avoid parser
execution completely.

## Decision

Pursue the optional prototype in [#225](https://github.com/baokhang83/blastradius/issues/225):

1. For a project class that a test dynamically executed, retain its directly declared
   method-invocation owner classes as potential targets.
2. During selection, if a changed project class is a retained direct potential target of an
   executed class, select that test and emit a reason naming the executed class and changed target.
3. Keep this one-hop, class-level expansion optional until replay results justify enabling it by
   default.

This is deliberately conservative. It can select a test although the executed method did not take
the branch containing the invocation. That over-attribution is observable in the selection reason
and is safer than leaving the dynamic edge invisible.

## Alternatives considered

### Mandatory whole-program static call graph — rejected

A general call graph needs a complete type hierarchy and an explicit entry model. The
[SootUp call graph documentation](https://soot-oss.github.io/SootUp/latest/callgraphs/) makes those
inputs explicit. Dynamic dispatch, reflection, generated code, and string routing still require
additional modelling. Making that broad analysis mandatory would complicate the dynamic core and
would likely over-select across ordinary projects.

### Generic cache instrumentation — rejected

Intercepting generic maps or cache libraries cannot establish which computation produced a value,
what key identifies it, when it is invalidated, whether a wrapper transforms it, or how concurrent
access changes attribution. A precise solution would be library-specific, which is not justified
before a concrete second case exists.

### Document the limitation permanently — rejected for now

The daily full suite remains the safety backstop, but the replay evidence is concentrated enough to
justify measuring a narrow deterministic fallback. The project should not silently expand to a
whole-program analysis merely to address this case.

## Success conditions and guardrails

- Measure #225 against the jsoup `QueryParser` replay case, including recovered killing tests and
  total selection expansion.
- Do not enable the fallback by default without that evidence.
- Retain the dynamic index as the primary source of dependencies.
- Do not introduce a transitive call graph or generic cache hook as part of #225.
- Every fallback selection must expose its concrete executed-class and changed-target reason.
- Keep analysis precomputed or cached so class-load processing does not add avoidable filesystem or
  syscall work.

## Constitution check

- **II. Simplicity:** one bounded, proven shape precedes any reusable analysis abstraction.
- **III. Safety over speed:** the rule only expands selection, and the daily full suite remains a
  complementary backstop.
- **IV. Deterministic core before ML:** the fallback is deterministic and optional.
- **V. Explainability:** every extra selection names both sides of the direct-reference rule.
- **VIII. No avoidable work on the hot path:** persistent metadata must be computed or cached
  without repeated filesystem work during class-load events.
