package io.github.baokhang83.blastradius.core.tracking;

import java.util.Map;

/**
 * One test's recorded dependencies, as persisted by {@link DependencyRecordWriter} and
 * read back by {@link DependencyRecordReader}. A list of these is the on-disk form of
 * {@code Map<TestIdentity, Map<String, String>>} — a flat list rather than a JSON object
 * keyed by the compound {@link TestIdentity}, to avoid a custom Jackson key
 * (de)serializer for a compound key type.
 */
public record DependencyRecord(TestIdentity test, Map<String, String> dependencies) {}
