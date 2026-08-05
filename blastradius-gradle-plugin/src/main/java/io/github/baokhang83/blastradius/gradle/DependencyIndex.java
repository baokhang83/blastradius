package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.index.DependencyIndexFormat;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

record DependencyIndex(int formatVersion, String anchorCommit, String builtAt, List<TestDependencyEntry> testDependencies,
        List<TestDirectInvocationEntry> directInvocations, Set<String> ambientDependencies) {

    DependencyIndex {
        formatVersion = DependencyIndexFormat.migrateLegacyVersion(formatVersion);
        directInvocations = directInvocations == null ? List.of() : List.copyOf(directInvocations);
        ambientDependencies = ambientDependencies == null ? Set.of() : Set.copyOf(ambientDependencies);
    }

    DependencyIndex(String anchorCommit, String builtAt, List<TestDependencyEntry> testDependencies) {
        this(anchorCommit, builtAt, testDependencies, List.of(), Set.of());
    }

    DependencyIndex(int formatVersion, String anchorCommit, String builtAt, List<TestDependencyEntry> testDependencies) {
        this(formatVersion, anchorCommit, builtAt, testDependencies, List.of(), Set.of());
    }

    DependencyIndex(String anchorCommit, String builtAt, List<TestDependencyEntry> testDependencies,
            List<TestDirectInvocationEntry> directInvocations, Set<String> ambientDependencies) {
        this(DependencyIndexFormat.CURRENT_VERSION, anchorCommit, builtAt,
                testDependencies, directInvocations, ambientDependencies);
    }

    record TestDependencyEntry(TestIdentity test, Set<String> dependsOnClasses) {}

    record TestDirectInvocationEntry(TestIdentity test, Map<String, Set<String>> sourceToTargetClasses) {}

    Map<TestIdentity, Set<String>> testDependenciesByTest() {
        return testDependencies.stream()
                .collect(Collectors.toUnmodifiableMap(TestDependencyEntry::test, TestDependencyEntry::dependsOnClasses));
    }

    Map<TestIdentity, Map<String, Set<String>>> directInvocationsByTest() {
        return directInvocations.stream().collect(Collectors.toUnmodifiableMap(
                TestDirectInvocationEntry::test, TestDirectInvocationEntry::sourceToTargetClasses));
    }

    boolean hasCurrentFormat() {
        return DependencyIndexFormat.isCurrentVersion(formatVersion);
    }
}
