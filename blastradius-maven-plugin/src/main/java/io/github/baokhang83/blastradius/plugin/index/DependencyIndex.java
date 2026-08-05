package io.github.baokhang83.blastradius.plugin.index;

import io.github.baokhang83.blastradius.core.index.DependencyIndexFormat;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The persisted, cacheable record a {@code TRACK} run produces and a {@code SELECT} run
 * consumes (data-model.md). {@code testDependencies} is a flat list rather than a JSON
 * object keyed by the compound {@link TestIdentity}, mirroring
 * {@code blastradius-core}'s {@code DependencyRecord} — avoids a custom Jackson key
 * (de)serializer for a compound key type.
 *
 * <p>Only class names are recorded, not the tracking agent's per-class bytecode
 * checksums (data-model.md) — the proven {@code SelectionEngine}/{@code
 * DependencyMatchSelector} logic only ever consumes class names.
 */
public record DependencyIndex(int formatVersion, String anchorCommit, String builtAt,
        List<TestDependencyEntry> testDependencies, List<TestDirectInvocationEntry> directInvocations,
        Set<String> ambientDependencies) {

    public DependencyIndex {
        formatVersion = DependencyIndexFormat.migrateLegacyVersion(formatVersion);
        directInvocations = directInvocations == null ? List.of() : List.copyOf(directInvocations);
        ambientDependencies = ambientDependencies == null ? Set.of() : Set.copyOf(ambientDependencies);
    }

    public DependencyIndex(String anchorCommit, String builtAt, List<TestDependencyEntry> testDependencies,
            Set<String> ambientDependencies) {
        this(anchorCommit, builtAt, testDependencies, List.of(), ambientDependencies);
    }

    /** Compatibility constructor for callers deliberately exercising an older format. */
    public DependencyIndex(int formatVersion, String anchorCommit, String builtAt,
            List<TestDependencyEntry> testDependencies, Set<String> ambientDependencies) {
        this(formatVersion, anchorCommit, builtAt, testDependencies, List.of(), ambientDependencies);
    }

    public DependencyIndex(String anchorCommit, String builtAt, List<TestDependencyEntry> testDependencies,
            List<TestDirectInvocationEntry> directInvocations, Set<String> ambientDependencies) {
        this(DependencyIndexFormat.CURRENT_VERSION, anchorCommit, builtAt,
                testDependencies, directInvocations, ambientDependencies);
    }

    /** Convenience for callers that don't populate {@code ambientDependencies} (mostly tests). */
    public DependencyIndex(String anchorCommit, String builtAt, List<TestDependencyEntry> testDependencies) {
        this(anchorCommit, builtAt, testDependencies, Set.of());
    }

    public record TestDependencyEntry(TestIdentity test, Set<String> dependsOnClasses) {}

    /** A test's dynamically-executed source classes and their declared direct target classes. */
    public record TestDirectInvocationEntry(TestIdentity test, Map<String, Set<String>> sourceToTargetClasses) {}

    /** {@code testDependencies}, reshaped into the map form the selection engine consumes. */
    public Map<TestIdentity, Set<String>> testDependenciesByTest() {
        return testDependencies.stream()
                .collect(Collectors.toUnmodifiableMap(TestDependencyEntry::test, TestDependencyEntry::dependsOnClasses));
    }

    /** Direct references reshaped into the map form the selection engine consumes. */
    public Map<TestIdentity, Map<String, Set<String>>> directInvocationsByTest() {
        return directInvocations.stream().collect(Collectors.toUnmodifiableMap(
                TestDirectInvocationEntry::test, TestDirectInvocationEntry::sourceToTargetClasses));
    }

    public boolean hasCurrentFormat() {
        return DependencyIndexFormat.isCurrentVersion(formatVersion);
    }
}
