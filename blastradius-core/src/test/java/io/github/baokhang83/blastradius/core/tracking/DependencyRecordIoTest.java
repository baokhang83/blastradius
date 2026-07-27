package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyRecordIoTest {

    private final DependencyRecordWriter writer = new DependencyRecordWriter();
    private final DependencyRecordReader reader = new DependencyRecordReader();

    @Test
    void writtenRecordsRoundTripThroughAFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("dependencies.json");

        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksAdd");
        TestIdentity barTest = new TestIdentity("com.example.BarTest", "checksSubtract");
        Map<TestIdentity, Map<String, String>> original = Map.of(
                fooTest, Map.of("com.example.Foo", "abc123", "com.example.Helper", "def456"),
                barTest, Map.of("com.example.Bar", "789xyz"));

        writer.write(file, original);
        assertTrue(Files.exists(file));

        Map<TestIdentity, Map<String, String>> roundTripped = reader.read(file);

        assertEquals(original, roundTripped);
    }

    @Test
    void emptyRecordSetRoundTrips(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("empty.json");

        writer.write(file, Map.of());
        Map<TestIdentity, Map<String, String>> roundTripped = reader.read(file);

        assertTrue(roundTripped.isEmpty());
    }

    @Test
    void testWithNullMethodNameRoundTrips(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("class-level.json");
        TestIdentity classLevel = new TestIdentity("com.example.FooTest", null);
        Map<TestIdentity, Map<String, String>> original = Map.of(classLevel, Map.of("com.example.Foo", "abc"));

        writer.write(file, original);
        Map<TestIdentity, Map<String, String>> roundTripped = reader.read(file);

        assertEquals(original, roundTripped);
    }

    @Test
    void readAllStripsATestsOwnClassNameFromTheMergedAmbientSet(@TempDir Path tempDir) throws Exception {
        // JUnit's discovery pass loads every discovered test class before any test's
        // tracking window opens, in every fork — so a test class is unconditionally
        // ambient, regardless of module or fork boundaries, and that membership must
        // never be mistaken for a real production dependency.
        Path base = tempDir.resolve("dependencies.json");
        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksFoo");
        writer.write(base.resolveSibling(base.getFileName() + ".111"),
                Map.of(fooTest, Map.of("com.example.Foo", "abc123")),
                Set.of("com.example.FooTest", "com.example.Foo", "java.lang.String"));

        DependencyRecordSet merged = reader.readAll(base);

        assertFalse(merged.ambientDependencies().contains("com.example.FooTest"),
                "a test's own class name must never remain in the ambient set");
        assertTrue(merged.ambientDependencies().contains("com.example.Foo"));
        assertTrue(merged.ambientDependencies().contains("java.lang.String"));
    }

    @Test
    void readAllStripsEveryTestsClassNameAcrossMultipleForks(@TempDir Path tempDir) throws Exception {
        // Simulates a multi-module reactor: each module's tests run in a separate fork
        // (sibling file), but the merged ambient set must exclude every known test class
        // across ALL forks, not just the ones tracked in the same fork.
        Path base = tempDir.resolve("dependencies.json");
        TestIdentity fooTest = new TestIdentity("com.example.a.FooTest", "checksFoo");
        TestIdentity usesFooTest = new TestIdentity("com.example.b.UsesFooTest", "checksFoo");
        writer.write(base.resolveSibling(base.getFileName() + ".111"),
                Map.of(fooTest, Map.of("com.example.a.Foo", "abc")),
                Set.of("com.example.a.FooTest", "com.example.b.UsesFooTest"));
        writer.write(base.resolveSibling(base.getFileName() + ".222"),
                Map.of(usesFooTest, Map.of("com.example.a.Foo", "abc")),
                Set.of());

        DependencyRecordSet merged = reader.readAll(base);

        assertFalse(merged.ambientDependencies().contains("com.example.a.FooTest"));
        assertFalse(merged.ambientDependencies().contains("com.example.b.UsesFooTest"));
    }
}
