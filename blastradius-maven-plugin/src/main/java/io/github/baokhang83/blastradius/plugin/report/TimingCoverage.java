package io.github.baokhang83.blastradius.plugin.report;

/** How much of the skipped set has a persisted execution-duration sample. */
public record TimingCoverage(int recordedSkippedTests, int totalSkippedTests) {

    public TimingCoverage {
        if (recordedSkippedTests < 0 || totalSkippedTests < 0 || recordedSkippedTests > totalSkippedTests) {
            throw new IllegalArgumentException("timing coverage must satisfy 0 <= recorded <= total");
        }
    }

    public static TimingCoverage none() {
        return new TimingCoverage(0, 0);
    }
}
