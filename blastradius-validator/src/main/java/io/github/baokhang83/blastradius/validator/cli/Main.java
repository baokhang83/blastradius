package io.github.baokhang83.blastradius.validator.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.validator.report.AnalysisReport;
import io.github.baokhang83.blastradius.validator.report.TextSummaryRenderer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI entry point. Usage:
 * {@code blastradius-validator run --project-path <path> --commits <N> --report-out <path> [--summary-out <path>]}
 */
public final class Main {

    public static void main(String[] args) {
        if (args.length == 0 || !"run".equals(args[0])) {
            System.err.println("usage: run --project-path <path> --commits <N> --report-out <path> [--summary-out <path>]");
            System.exit(2);
            return;
        }

        Path projectPath = null;
        Integer commits = null;
        Path reportOut = null;
        Path summaryOut = null;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--project-path" -> projectPath = Path.of(args[++i]);
                case "--commits" -> commits = Integer.parseInt(args[++i]);
                case "--report-out" -> reportOut = Path.of(args[++i]);
                case "--summary-out" -> summaryOut = Path.of(args[++i]);
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
            RunConfig config = new RunConfig(projectPath, commits, reportOut);
            int exitCode = new RunCommand().run(config);
            printSummary(reportOut, summaryOut, exitCode);
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
}
