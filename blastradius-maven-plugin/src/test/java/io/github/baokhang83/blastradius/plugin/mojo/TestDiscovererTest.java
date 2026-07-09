package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Self-referential: discovers tests from this very module's own already-compiled test
 * classes, rather than needing a separately-compiled fixture project.
 */
class TestDiscovererTest {

    private final TestDiscoverer discoverer = new TestDiscoverer();

    @Test
    void discoversARealKnownTestFromThisModulesOwnCompiledClasses() {
        Path testClassesDir = Path.of("target", "test-classes");
        List<String> classpathElements = List.of(
                Path.of("target", "classes").toAbsolutePath().toString(),
                testClassesDir.toAbsolutePath().toString());

        Set<TestIdentity> discovered = discoverer.discoverAllTests(testClassesDir, classpathElements);

        TestIdentity knownTest = new TestIdentity(
                "io.github.baokhang83.blastradius.plugin.index.DependencyIndexIoTest",
                "writtenIndexRoundTripsThroughAFile");
        assertTrue(discovered.contains(knownTest),
                "expected to discover " + knownTest + " among " + discovered.size() + " discovered tests");
    }
}
