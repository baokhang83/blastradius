package io.github.baokhang83.blastradius.plugin.report;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Objects;

/** One observed test duration retained for a future selection estimate. */
public record TestTiming(TestIdentity test, long durationMillis) {

    public TestTiming {
        Objects.requireNonNull(test, "test");
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
    }
}
