package io.github.baokhang83.blastradius.core.tracking;

import java.util.Map;
import java.util.Set;

/**
 * Every {@code <baseOutputFile>.<pid>} sibling file merged into one: per-test dependencies
 * unioned across forks, and the fork-wide {@code ambientDependencies} — classes loaded before
 * the first test's tracking window opened in any fork — also unioned, since each fork's
 * discovery pass can force-load a different subset.
 */
public record DependencyRecordSet(
        Map<TestIdentity, Map<String, String>> tests,
        Map<TestIdentity, Map<String, Set<String>>> directInvocations,
        Set<String> ambientDependencies) {

    /** Preserves callers built against the format-2 in-memory representation. */
    public DependencyRecordSet(Map<TestIdentity, Map<String, String>> tests, Set<String> ambientDependencies) {
        this(tests, Map.of(), ambientDependencies);
    }
}
