package io.github.baokhang83.blastradius.validator.report;

/**
 * Aggregate execution-savings evidence across a completed run (FR-008 / SC-003).
 *
 * <p>Invariant: {@code dependencyMatchedSelections + fallbackDrivenSelections +
 * newOrModifiedTestSelections == totalSelected}.
 *
 * @param totalTestExecutions          total test executions across all analyzed pairs
 * @param totalSelected                the count that would have been selected
 * @param proportionSkipped            {@code 1 - (totalSelected / totalTestExecutions)}
 * @param fallbackDrivenSelections     subset attributable to the conservative fallback rule
 * @param dependencyMatchedSelections  subset attributable to ordinary dependency matching
 * @param newOrModifiedTestSelections  subset attributable to new/modified tests
 */
public record SavingsSummary(
        int totalTestExecutions,
        int totalSelected,
        double proportionSkipped,
        int fallbackDrivenSelections,
        int dependencyMatchedSelections,
        int newOrModifiedTestSelections) {}
