package io.github.baokhang83.blastradius.core.tracking;

import java.util.List;
import java.util.Set;

/**
 * On-disk shape of a single {@code <prefix>.<pid>} file: one JVM fork's per-test dependency
 * records plus the classes it loaded before any test's tracking window opened (see
 * {@link DependencyTrackingAgent#ambientDependencies()}). Purely an ephemeral intra-build
 * protocol between {@link DependencyRecordWriter} and {@link DependencyRecordReader} — never
 * persisted across builds — so its shape is free to change without a version migration.
 */
record DependencyRecordFile(
        List<DependencyRecord> tests,
        List<DirectInvocationRecord> directInvocations,
        Set<String> ambientDependencies) {

    DependencyRecordFile {
        directInvocations = directInvocations == null ? List.of() : List.copyOf(directInvocations);
        ambientDependencies = ambientDependencies == null ? Set.of() : Set.copyOf(ambientDependencies);
    }
}
