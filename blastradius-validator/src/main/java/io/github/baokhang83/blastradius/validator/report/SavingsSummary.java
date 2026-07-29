package io.github.baokhang83.blastradius.validator.report;

/**
 * Aggregate execution-savings evidence across a completed run (FR-008 / SC-003).
 *
 * <p>Invariant: {@code dependencyMatchedSelections + fallbackDrivenSelections +
 * moduleScopedFallbackSelections + newOrModifiedTestSelections == totalSelected}.
 *
 * @param totalTestExecutions           total test executions across all analyzed pairs
 * @param totalSelected                 the count that would have been selected
 * @param proportionSkipped             {@code 1 - (totalSelected / totalTestExecutions)}
 * @param fallbackDrivenSelections      subset attributable to the whole-suite fallback rule
 * @param moduleScopedFallbackSelections subset attributable to the reactor-scoped fallback
 *                                      (a NON_SOURCE change scoped to a changed module and its
 *                                      dependents) — the savings this narrowing recovers over
 *                                      the whole-suite fallback
 * @param dependencyMatchedSelections   subset attributable to ordinary dependency matching
 * @param newOrModifiedTestSelections   subset attributable to new/modified tests
 */
public record SavingsSummary(
        int totalTestExecutions,
        int totalSelected,
        double proportionSkipped,
        int fallbackDrivenSelections,
        int moduleScopedFallbackSelections,
        int dependencyMatchedSelections,
        int newOrModifiedTestSelections) {}
