package io.github.baokhang83.blastradius.validator.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.validator.git.HistoryMode;
import io.github.baokhang83.blastradius.validator.build.SkippedTests;
import io.github.baokhang83.blastradius.validator.mutation.MutationCommand;
import io.github.baokhang83.blastradius.validator.mutation.MutationConfig;
import io.github.baokhang83.blastradius.validator.mutation.MutationReport;
import io.github.baokhang83.blastradius.validator.mutation.MutationTextSummaryRenderer;
import io.github.baokhang83.blastradius.validator.report.AnalysisReport;
import io.github.baokhang83.blastradius.validator.report.TextSummaryRenderer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI entry point. Usage:
 * {@code blastradius-validator run --project-path <path> --commits <N> --report-out <path>
 * [--summary-out <path>] [--maven-threads <N>] [--fast-ground-truth] [--build-concurrency <K>]
 * [--build-timeout-minutes <M>] [--skip-build-extras] [--history-mode <all-parents|first-parent>]}
 */
public final class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(2);
            return;
        }
        switch (args[0]) {
            case "run" -> runHistory(args);
            case "mutate" -> runMutations(args);
            default -> {
                usage();
                System.exit(2);
            }
        }
    }

    private static void runHistory(String[] args) {

        Path projectPath = null;
        Integer commits = null;
        Path reportOut = null;
        Path summaryOut = null;
        Integer mavenThreads = null;
        boolean fastGroundTruth = false;
        int buildConcurrency = 1;
        long buildTimeoutMinutes = RunConfig.DEFAULT_BUILD_TIMEOUT_MINUTES;
        boolean skipBuildExtras = false;
        HistoryMode historyMode = HistoryMode.ALL_PARENTS;
        List<String> skippedTestValues = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--project-path" -> projectPath = Path.of(args[++i]);
                case "--commits" -> commits = Integer.parseInt(args[++i]);
                case "--report-out" -> reportOut = Path.of(args[++i]);
                case "--summary-out" -> summaryOut = Path.of(args[++i]);
                case "--maven-threads" -> mavenThreads = Integer.parseInt(args[++i]);
                case "--fast-ground-truth" -> fastGroundTruth = true;
                case "--build-concurrency" -> buildConcurrency = Integer.parseInt(args[++i]);
                case "--build-timeout-minutes" -> buildTimeoutMinutes = Long.parseLong(args[++i]);
                case "--skip-build-extras" -> skipBuildExtras = true;
                case "--history-mode" -> historyMode = HistoryMode.fromCliValue(args[++i]);
                case "--skipped-tests" -> skippedTestValues.add(args[++i]);
                default -> {
                    System.err.println("unknown argument: " + args[i]);
                    System.exit(2);
                    return;
                }
            }
        }

        if (projectPath == null || commits == null || reportOut == null) {
            System.err.println("missing required argument(s): --project-path, --commits, --report-out are all required");
            System.exit(2);
            return;
        }

        try {
            RunConfig config = new RunConfig(projectPath, commits, reportOut, mavenThreads, fastGroundTruth,
                    buildConcurrency, buildTimeoutMinutes, skipBuildExtras, historyMode,
                    SkippedTests.parse(skippedTestValues));
            int exitCode = new RunCommand().run(config);
            printSummary(reportOut, summaryOut, exitCode);
            System.exit(exitCode);
        } catch (IllegalArgumentException e) {
            System.err.println("invalid configuration: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void runMutations(String[] args) {
        Path projectPath = null;
        Path reportOut = null;
        Path summaryOut = null;
        String classFilter = null;
        int maxMutationClasses = MutationConfig.DEFAULT_MAX_CLASSES;
        int maxMutations = MutationConfig.DEFAULT_MAX_MUTATIONS;
        long timeLimitMinutes = MutationConfig.DEFAULT_TIME_LIMIT_MINUTES;
        Integer mavenThreads = null;
        long buildTimeoutMinutes = MutationConfig.DEFAULT_BUILD_TIMEOUT_MINUTES;
        boolean skipBuildExtras = false;
        List<String> skippedTestValues = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--project-path" -> projectPath = Path.of(args[++i]);
                case "--report-out" -> reportOut = Path.of(args[++i]);
                case "--summary-out" -> summaryOut = Path.of(args[++i]);
                case "--mutation-class" -> classFilter = args[++i];
                case "--max-mutation-classes" -> maxMutationClasses = Integer.parseInt(args[++i]);
                case "--max-mutations" -> maxMutations = Integer.parseInt(args[++i]);
                case "--mutation-time-limit-minutes" -> timeLimitMinutes = Long.parseLong(args[++i]);
                case "--maven-threads" -> mavenThreads = Integer.parseInt(args[++i]);
                case "--build-timeout-minutes" -> buildTimeoutMinutes = Long.parseLong(args[++i]);
                case "--skip-build-extras" -> skipBuildExtras = true;
                case "--skipped-tests" -> skippedTestValues.add(args[++i]);
                default -> {
                    System.err.println("unknown argument: " + args[i]);
                    System.exit(2);
                    return;
                }
            }
        }
        if (projectPath == null || reportOut == null) {
            System.err.println("missing required argument(s): --project-path and --report-out are required");
            System.exit(2);
            return;
        }
        try {
            MutationConfig config = new MutationConfig(projectPath, reportOut, classFilter, maxMutationClasses, maxMutations,
                    timeLimitMinutes, mavenThreads, buildTimeoutMinutes, skipBuildExtras,
                    SkippedTests.parse(skippedTestValues));
            int exitCode = new MutationCommand().run(config);
            printMutationSummary(reportOut, summaryOut, exitCode);
            System.exit(exitCode);
        } catch (IllegalArgumentException e) {
            System.err.println("invalid configuration: " + e.getMessage());
            System.exit(2);
        }
    }

    /** Renders the just-written report as text — to stdout by default, or {@code --summary-out}. */
    private static void printSummary(Path reportOut, Path summaryOut, int exitCode) {
        if (exitCode == 2) {
            return; // the run itself never completed; there is no report to render
        }
        try {
            AnalysisReport report = new ObjectMapper().readValue(reportOut.toFile(), AnalysisReport.class);
            String text = new TextSummaryRenderer().render(report);
            if (summaryOut != null) {
                Files.writeString(summaryOut, text);
            } else {
                System.out.print(text);
            }
        } catch (IOException e) {
            System.err.println("warning: could not render text summary: " + e.getMessage());
        }
    }

    private static void printMutationSummary(Path reportOut, Path summaryOut, int exitCode) {
        if (exitCode == 2) {
            return;
        }
        try {
            MutationReport report = new ObjectMapper().readValue(reportOut.toFile(), MutationReport.class);
            String text = new MutationTextSummaryRenderer().render(report);
            if (summaryOut != null) {
                Files.writeString(summaryOut, text);
            } else {
                System.out.print(text);
            }
        } catch (IOException e) {
            System.err.println("warning: could not render mutation text summary: " + e.getMessage());
        }
    }

    private static void usage() {
        System.err.println("usage: run --project-path <path> --commits <N> --report-out <path> "
                + "[--summary-out <path>] [--maven-threads <N>] [--fast-ground-truth] "
                + "[--build-concurrency <K>] [--build-timeout-minutes <M>] [--skip-build-extras] "
                + "[--history-mode <all-parents|first-parent>] [--skipped-tests <FQCN,...>]\n"
                + "   or: mutate --project-path <path> --report-out <path> [--summary-out <path>] "
                + "[--mutation-class <FQCN>] [--max-mutation-classes <N>] [--max-mutations <N>] "
                + "[--mutation-time-limit-minutes <M>] [--maven-threads <N>] "
                + "[--build-timeout-minutes <M>] [--skip-build-extras] [--skipped-tests <FQCN,...>]");
    }
}
