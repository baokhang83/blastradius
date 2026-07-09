package io.github.baokhang83.blastradius.validator.build;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Runs a target project's full suite once (optionally with the tracking agent attached),
 * then re-runs each failed test once to confirm it isn't flaky (FR-013), producing a
 * {@link GroundTruthResult} per test with outcome {@code PASSED}, {@code CONFIRMED_FAILED},
 * or {@code FLAKY}.
 *
 * <p>Scans for {@code surefire-reports}/{@code failsafe-reports} directories anywhere
 * under the project root, not just at the root itself, so multi-module reactors (FR-011)
 * are handled without special-casing.
 */
public final class GroundTruthResolver {

    private final MavenBuildRunner buildRunner;
    private final SurefireReportParser reportParser;

    public GroundTruthResolver() {
        this(new MavenBuildRunner(), new SurefireReportParser());
    }

    GroundTruthResolver(MavenBuildRunner buildRunner, SurefireReportParser reportParser) {
        this.buildRunner = buildRunner;
        this.reportParser = reportParser;
    }

    public List<GroundTruthResult> resolve(Path projectDir, Path agentJar, Path dependencyRecordOutputFile) {
        buildRunner.run(projectDir, agentJar, dependencyRecordOutputFile);
        Map<TestIdentity, Boolean> initialResults = parseAllReports(projectDir);

        List<GroundTruthResult> results = new ArrayList<>();
        for (Map.Entry<TestIdentity, Boolean> entry : initialResults.entrySet()) {
            TestIdentity test = entry.getKey();
            boolean passed = entry.getValue();
            if (passed) {
                results.add(new GroundTruthResult(test, Outcome.PASSED));
                continue;
            }
            results.add(new GroundTruthResult(test, confirmFailure(projectDir, test)));
        }
        return results;
    }

    private Outcome confirmFailure(Path projectDir, TestIdentity test) {
        buildRunner.runSingleTest(projectDir, test);
        Boolean confirmedPassed = parseAllReports(projectDir).get(test);
        return Boolean.TRUE.equals(confirmedPassed) ? Outcome.FLAKY : Outcome.CONFIRMED_FAILED;
    }

    private Map<TestIdentity, Boolean> parseAllReports(Path projectDir) {
        Map<TestIdentity, Boolean> merged = new HashMap<>();
        for (Path reportsDir : findReportsDirectories(projectDir)) {
            merged.putAll(reportParser.parse(reportsDir));
        }
        return merged;
    }

    private static List<Path> findReportsDirectories(Path projectDir) {
        try (Stream<Path> stream = Files.walk(projectDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("surefire-reports") || name.equals("failsafe-reports");
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to scan for test reports under " + projectDir, e);
        }
    }
}
