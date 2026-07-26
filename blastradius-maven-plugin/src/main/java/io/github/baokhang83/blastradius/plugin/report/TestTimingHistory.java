package io.github.baokhang83.blastradius.plugin.report;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned, cacheable timing samples written independently of selection correctness. */
public record TestTimingHistory(int formatVersion, List<TestTiming> timings) {

    public static final int FORMAT_VERSION = 1;

    public TestTimingHistory {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported timing history format version: " + formatVersion);
        }
        timings = List.copyOf(timings);
    }

    public static TestTimingHistory empty() {
        return new TestTimingHistory(FORMAT_VERSION, List.of());
    }

    public static TestTimingHistory from(Map<TestIdentity, Long> durationsByTest) {
        return new TestTimingHistory(FORMAT_VERSION, durationsByTest.entrySet().stream()
                .sorted(java.util.Comparator
                        .comparing((Map.Entry<TestIdentity, Long> entry) -> entry.getKey().className())
                        .thenComparing(entry -> entry.getKey().methodName(),
                                java.util.Comparator.nullsFirst(String::compareTo)))
                .map(entry -> new TestTiming(entry.getKey(), entry.getValue()))
                .toList());
    }

    public Map<TestIdentity, Long> durationsByTest() {
        Map<TestIdentity, Long> durations = new LinkedHashMap<>();
        timings.forEach(timing -> durations.put(timing.test(), timing.durationMillis()));
        return Map.copyOf(durations);
    }
}
