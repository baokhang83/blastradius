package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

record DependencyIndex(String anchorCommit, String builtAt, List<TestDependencyEntry> testDependencies) {

    record TestDependencyEntry(TestIdentity test, Set<String> dependsOnClasses) {}

    Map<TestIdentity, Set<String>> testDependenciesByTest() {
        return testDependencies.stream()
                .collect(Collectors.toUnmodifiableMap(TestDependencyEntry::test, TestDependencyEntry::dependsOnClasses));
    }
}
