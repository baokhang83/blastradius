package io.github.baokhang83.blastradius.validator.build;

import java.util.List;
import java.util.Locale;

/**
 * Recognizes a build failure caused by a flaky network hop to a Maven repository — a dropped
 * connection or DNS blip mid-download — as opposed to a genuine compile or dependency-resolution
 * error that would fail again on retry. {@link CommitBuildService} uses this to decide whether a
 * failed build is worth retrying: retrying a real compile error just burns wall-clock, while a
 * transient network failure recovers as soon as connectivity does.
 *
 * <p>Matched against {@link CommitBuild#failureReason()}, which embeds the tail of the raw Maven
 * output (see {@code RunCommand#tail}), so Maven's own transfer-failure wording is what's tested
 * against here.
 */
final class TransientBuildFailureDetector {

    private static final List<String> NETWORK_MARKERS = List.of(
            "network is unreachable",
            "could not transfer artifact",
            "connection timed out",
            "connect timed out",
            "connection refused",
            "no route to host",
            "read timed out",
            "unknownhostexception",
            "temporary failure in name resolution",
            "software caused connection abort");

    private TransientBuildFailureDetector() {
    }

    static boolean isTransient(String failureReason) {
        if (failureReason == null) {
            return false;
        }
        String lower = failureReason.toLowerCase(Locale.ROOT);
        return NETWORK_MARKERS.stream().anyMatch(lower::contains);
    }
}
