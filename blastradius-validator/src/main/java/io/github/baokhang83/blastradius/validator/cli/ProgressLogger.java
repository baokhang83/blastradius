package io.github.baokhang83.blastradius.validator.cli;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * Emits timestamped, human-readable progress lines while a run is in flight, so the jar is
 * no longer silent for the many minutes a real run takes. Writes to <em>stderr</em> by
 * design: the machine-readable {@code AnalysisReport} goes to {@code --report-out} (and the
 * text summary to stdout), so narration here never contaminates either.
 *
 * <p>Thread-safe: {@link #buildStarted}/{@link #buildFinished} are called from the
 * concurrent build workers (see CommitBuildService), so every emit is synchronized to keep
 * a line from interleaving with another thread's.
 */
public final class ProgressLogger {

    private final PrintStream out;
    private final LongSupplier epochMillis;
    private final boolean enabled;

    ProgressLogger(PrintStream out, LongSupplier epochMillis) {
        this(out, epochMillis, true);
    }

    private ProgressLogger(PrintStream out, LongSupplier epochMillis, boolean enabled) {
        this.out = out;
        this.epochMillis = epochMillis;
        this.enabled = enabled;
    }

    /** Logs to {@code System.err} with a wall-clock timestamp source. */
    public static ProgressLogger toStderr() {
        return new ProgressLogger(System.err, System::currentTimeMillis, true);
    }

    /** A no-op logger — for callers (and tests) that want the pipeline silent. */
    public static ProgressLogger silent() {
        PrintStream discard = new PrintStream(OutputStream.nullOutputStream(), false, StandardCharsets.UTF_8);
        return new ProgressLogger(discard, () -> 0L, false);
    }

    public void windowResolved(int pairCount) {
        line("resolved window of " + pairCount + " commit pair(s)");
    }

    public void buildStarted(String sha, String role) {
        line("building " + shortSha(sha) + " [" + role + "] ...");
    }

    public void buildFinished(String sha, String role, long millis) {
        line("built " + shortSha(sha) + " [" + role + "] in " + duration(millis));
    }

    public void buildCached(String sha, String role) {
        line("cached " + shortSha(sha) + " [" + role + "] — reused from a prior run");
    }

    public void buildFailed(String sha, String role, String reason) {
        line("build FAILED " + shortSha(sha) + " [" + role + "]: " + reason);
    }

    public void buildRetrying(String sha, String role, int attempt, int maxAttempts, String reason) {
        line("build failed " + shortSha(sha) + " [" + role + "] (transient, retry " + attempt + "/"
                + maxAttempts + "): " + reason);
    }

    public void pairCompleted(int index, int total, int wouldMissCount, long millis) {
        line("pair " + index + "/" + total + " done in " + duration(millis)
                + " — " + wouldMissCount + " would-miss case(s)");
    }

    public void pairExcluded(int index, int total, String reason) {
        line("pair " + index + "/" + total + " excluded: " + reason);
    }

    public void summary(String verdict, long totalMillis) {
        line("run complete: " + verdict + " in " + duration(totalMillis));
    }

    private synchronized void line(String message) {
        if (!enabled) {
            return;
        }
        out.println("[" + Instant.ofEpochMilli(epochMillis.getAsLong()) + "] " + message);
    }

    /** First 7 chars of a git sha, or the whole thing if shorter — enough to scan by eye. */
    private static String shortSha(String sha) {
        if (sha == null) {
            return "?";
        }
        return sha.length() <= 7 ? sha : sha.substring(0, 7);
    }

    /**
     * Renders a millisecond span as {@code Xh Ym Zs} / {@code Ym Zs} / {@code Zs}, dropping
     * higher units that are zero. Sub-second spans show one decimal (e.g. {@code 0.5s}) so a
     * fast step doesn't collapse to a bare {@code 0s}.
     */
    static String duration(long millis) {
        if (millis < 1000) {
            double seconds = millis / 1000.0;
            return (Math.round(seconds * 10.0) / 10.0) + "s";
        }
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (hours > 0 || minutes > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append('s');
        return sb.toString();
    }
}
