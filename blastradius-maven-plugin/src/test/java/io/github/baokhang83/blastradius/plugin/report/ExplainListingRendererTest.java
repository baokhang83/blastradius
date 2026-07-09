package io.github.baokhang83.blastradius.plugin.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.selection.SelectionDecision;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.IndexApplicability;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests (tasks.md T036) for the {@code -Dblastradius.explain=true} expanded per-test listing
 * (SC-004) — every test's selected/reason/matchedChangedClass, not just the aggregate summary
 * {@link ConsoleSummaryRenderer} produces.
 */
class ExplainListingRendererTest {

    private final ExplainListingRenderer renderer = new ExplainListingRenderer();

    @Test
    void listsEveryTestWithItsReasonAndMatchedClass() {
        TestIdentity matched = new TestIdentity("com.example.FooTest", "checksFoo");
        TestIdentity unrelated = new TestIdentity("com.example.BarTest", "checksBar");
        List<SelectionDecision> decisions = List.of(
                SelectionDecision.dependencyMatch(matched, "com.example.Foo"),
                SelectionDecision.noMatch(unrelated));
        BuildReport report = BuildReport.forSelect(
                IndexApplicability.applicable(new DependencyIndex("abc123", "2026-07-09T10:00:00Z", List.of())),
                decisions);

        List<String> lines = renderer.render(report);

        assertEquals(2, lines.size());
        assertTrue(lines.stream().anyMatch(l -> l.contains("com.example.FooTest#checksFoo")
                && l.contains("selected") && l.contains("DEPENDENCY_MATCH") && l.contains("com.example.Foo")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("com.example.BarTest#checksBar")
                && l.contains("skipped") && l.contains("NO_MATCH")));
    }

    @Test
    void anEmptyDecisionListRendersNoLines() {
        BuildReport report = BuildReport.forSelect(
                IndexApplicability.applicable(new DependencyIndex("abc123", "2026-07-09T10:00:00Z", List.of())), List.of());

        assertEquals(List.of(), renderer.render(report));
    }
}
