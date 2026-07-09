package io.github.baokhang83.blastradius.plugin.report;

import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Renders the {@code -Dblastradius.explain=true} expanded per-test listing (tasks.md T036,
 * SC-004): every test's {@code selected}/{@code reason}/{@code matchedChangedClass}, not
 * just {@link ConsoleSummaryRenderer}'s aggregate summary.
 */
public final class ExplainListingRenderer {

    public List<String> render(BuildReport report) {
        return report.decisions().stream()
                .sorted(Comparator.comparing(ExplainListingRenderer::testLabel))
                .map(ExplainListingRenderer::renderLine)
                .toList();
    }

    private static String renderLine(SelectionDecision decision) {
        String status = decision.selected() ? "selected" : "skipped";
        String matched = decision.matchedChangedClass() == null
                ? ""
                : " matchedChangedClass=" + decision.matchedChangedClass();
        return "[blastradius]   " + testLabel(decision) + " — " + status + " reason=" + decision.reason() + matched;
    }

    private static String testLabel(SelectionDecision decision) {
        return testLabel(decision.test());
    }

    private static String testLabel(TestIdentity test) {
        return test.methodName() == null ? test.className() : test.className() + "#" + test.methodName();
    }
}
