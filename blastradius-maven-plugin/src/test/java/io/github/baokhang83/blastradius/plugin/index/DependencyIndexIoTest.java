package io.github.baokhang83.blastradius.plugin.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex.TestDependencyEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyIndexIoTest {

    private final DependencyIndexWriter writer = new DependencyIndexWriter();
    private final DependencyIndexReader reader = new DependencyIndexReader();

    @Test
    void writtenIndexRoundTripsThroughAFile(@TempDir Path tempDir) {
        Path file = tempDir.resolve("index.json");
        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksAdd");
        DependencyIndex original = new DependencyIndex(
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                "2026-07-09T10:03:00Z",
                List.of(new TestDependencyEntry(fooTest, Set.of("com.example.Foo", "com.example.Helper"))));

        writer.write(file, original);
        assertTrue(Files.exists(file));

        DependencyIndex roundTripped = reader.read(file);

        assertEquals(original, roundTripped);
    }

    @Test
    void emptyTestDependenciesRoundTrips(@TempDir Path tempDir) {
        Path file = tempDir.resolve("empty.json");
        DependencyIndex original = new DependencyIndex("a1b2c3d", "2026-07-09T10:03:00Z", List.of());

        writer.write(file, original);
        DependencyIndex roundTripped = reader.read(file);

        assertEquals(original, roundTripped);
    }

    @Test
    void testDependenciesByTestExposesAMapKeyedByTestIdentity() {
        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksAdd");
        TestIdentity barTest = new TestIdentity("com.example.BarTest", "checksSubtract");
        DependencyIndex index = new DependencyIndex("a1b2c3d", "2026-07-09T10:03:00Z", List.of(
                new TestDependencyEntry(fooTest, Set.of("com.example.Foo")),
                new TestDependencyEntry(barTest, Set.of("com.example.Bar"))));

        var byTest = index.testDependenciesByTest();

        assertEquals(Set.of("com.example.Foo"), byTest.get(fooTest));
        assertEquals(Set.of("com.example.Bar"), byTest.get(barTest));
    }

    @Test
    void readingAMissingFileThrows(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.json");
        org.junit.jupiter.api.function.Executable readMissing = () -> reader.read(missing);
        org.junit.jupiter.api.Assertions.assertThrows(java.io.UncheckedIOException.class, readMissing);
    }
}
