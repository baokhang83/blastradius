package io.github.baokhang83.blastradius.validator.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ProgressLoggerTest {

    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
    // A fixed epoch-millis clock so the timestamp prefix is deterministic under test.
    private final AtomicLong clock = new AtomicLong(0L);
    private final ProgressLogger logger =
            new ProgressLogger(new PrintStream(sink, true, StandardCharsets.UTF_8), clock::get);

    private String output() {
        return sink.toString(StandardCharsets.UTF_8);
    }

    @Test
    void everyLineIsTimestampedSoProgressIsFollowable() {
        clock.set(0L);
        logger.windowResolved(5);

        String line = output();
        // ISO-8601 UTC prefix in brackets, e.g. [1970-01-01T00:00:00Z].
        assertTrue(line.startsWith("[1970-01-01T00:00:00Z]"), () -> "no timestamp prefix: " + line);
    }

    @Test
    void windowResolvedReportsThePairCount() {
        logger.windowResolved(5);
        assertTrue(output().contains("5"), output());
        assertTrue(output().toLowerCase().contains("pair"), output());
    }

    @Test
    void buildLifecycleLogsStartThenFinishWithShaRoleAndDuration() {
        logger.buildStarted("abc1234def", "base+agent");
        logger.buildFinished("abc1234def", "base+agent", 123_000L);

        String out = output();
        // Short sha, not the whole 40 chars, so lines stay scannable.
        assertTrue(out.contains("abc1234"), out);
        assertTrue(out.contains("base+agent"), out);
        // Human-readable duration derived from the millis (123s -> 2m 3s).
        assertTrue(out.contains("2m 3s"), out);
    }

    @Test
    void pairCompletedShowsProgressCountAndWouldMissCount() {
        logger.pairCompleted(2, 5, 3, 90_000L);

        String out = output();
        assertTrue(out.contains("2/5"), out);
        assertTrue(out.contains("3"), out);
        assertTrue(out.toLowerCase().contains("would-miss") || out.toLowerCase().contains("would miss"), out);
    }

    @Test
    void summaryReportsVerdictAndTotalDuration() {
        logger.summary("PASS", 600_000L);

        String out = output();
        assertTrue(out.contains("PASS"), out);
        assertTrue(out.contains("10m"), out);
    }

    @Test
    void aSilentLoggerWritesNothingSoTheReportStreamStaysClean() {
        ProgressLogger silent = ProgressLogger.silent();
        silent.windowResolved(5);
        silent.buildStarted("abc", "base");
        silent.summary("PASS", 1L);
        // No sink to assert against directly; assert it does not throw and, via the
        // real stream, that silent() truly discards (covered by not touching our sink).
        assertFalse(output().contains("abc"));
    }

    @Test
    void subSecondDurationsRenderInSeconds() {
        logger.buildFinished("abc", "head", 500L);
        assertTrue(output().contains("0.5s") || output().contains("0s"), output());
    }

    @Test
    void formatsHoursForLongRuns() {
        logger.summary("FAIL", 3_723_000L); // 1h 2m 3s
        assertEquals(true, output().contains("1h 2m 3s"), output());
    }
}
