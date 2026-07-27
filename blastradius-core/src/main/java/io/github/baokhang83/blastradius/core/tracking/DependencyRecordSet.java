package io.github.baokhang83.blastradius.core.tracking;

import java.util.Map;
import java.util.Set;

/**
 * Every {@code <baseOutputFile>.<pid>} sibling file merged into one: per-test dependencies
 * unioned across forks, and the fork-wide {@code ambientDependencies} — classes loaded before
 * the first test's tracking window opened in any fork — also unioned, since each fork's
 * discovery pass can force-load a different subset.
 */
public record DependencyRecordSet(Map<TestIdentity, Map<String, String>> tests, Set<String> ambientDependencies) {}
