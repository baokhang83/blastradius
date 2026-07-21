package io.github.baokhang83.blastradius.plugin.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.index.DependencyIndexFormat;
import io.github.baokhang83.blastradius.core.index.FileIndexStore;
import io.github.baokhang83.blastradius.core.index.IndexStore;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex.TestDependencyEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyIndexIoTest {

    @Test
    void writtenIndexRoundTripsThroughAFile(@TempDir Path tempDir) throws Exception {
        IndexStore<DependencyIndex> store = new FileIndexStore<>(tempDir, DependencyIndex.class);
        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksAdd");
        DependencyIndex original = new DependencyIndex(
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                "2026-07-09T10:03:00Z",
                List.of(new TestDependencyEntry(fooTest, Set.of("com.example.Foo", "com.example.Helper"))));

        store.put("index.json", original);
        assertTrue(Files.exists(tempDir.resolve("index.json")));
        assertTrue(Files.readString(tempDir.resolve("index.json"))
                .contains("\"formatVersion\":" + DependencyIndexFormat.CURRENT_VERSION));

        DependencyIndex roundTripped = store.get("index.json").orElseThrow();

        assertEquals(original, roundTripped);
    }

    @Test
    void emptyTestDependenciesRoundTrips(@TempDir Path tempDir) {
        IndexStore<DependencyIndex> store = new FileIndexStore<>(tempDir, DependencyIndex.class);
        DependencyIndex original = new DependencyIndex("a1b2c3d", "2026-07-09T10:03:00Z", List.of());

        store.put("empty.json", original);
        DependencyIndex roundTripped = store.get("empty.json").orElseThrow();

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
    void missingKeyReturnsEmpty(@TempDir Path tempDir) {
        IndexStore<DependencyIndex> store = new FileIndexStore<>(tempDir, DependencyIndex.class);

        assertTrue(store.get("does-not-exist.json").isEmpty());
    }

    @Test
    void unversionedLegacyIndexMigratesToTheCurrentFormat(@TempDir Path tempDir) throws Exception {
        IndexStore<DependencyIndex> store = new FileIndexStore<>(tempDir, DependencyIndex.class);
        Files.writeString(tempDir.resolve("legacy.json"), """
                {
                  "anchorCommit": "a1b2c3d",
                  "builtAt": "2026-07-09T10:03:00Z",
                  "testDependencies": []
                }
                """);

        DependencyIndex migrated = store.get("legacy.json").orElseThrow();

        assertEquals(DependencyIndexFormat.CURRENT_VERSION, migrated.formatVersion());
    }
}
