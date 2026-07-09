package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
}
