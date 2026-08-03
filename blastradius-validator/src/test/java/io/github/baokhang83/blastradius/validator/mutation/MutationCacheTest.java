package io.github.baokhang83.blastradius.validator.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.build.SkippedTests;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MutationCacheTest {

    private static CommitPair pair() {
        return CommitPair.analyzed("base1", "head1",
                List.of(new ChangedFile("src/main/java/com/example/Foo.java", FileKind.JAVA_SOURCE, "com.example.Foo")));
    }

    private static MutationCandidate candidate() {
        return new MutationCandidate(
                "src/main/java/com/example/Foo.java", "com.example.Foo",
                MutationOperator.RELATIONAL_OPERATOR, 42, "<", ">");
    }

    private static MutationExperiment killed(CommitPair pair, MutationCandidate candidate) {
        TestIdentity killer = new TestIdentity("com.example.FooTest", "detectsIt");
        return new MutationExperiment(pair, candidate, MutationCandidateOrigin.DIFF_TARGETED, "mutant1",
                MutationStatus.KILLED, null,
                List.of(), List.of(killer), List.of(killer), List.of(), List.of());
    }

    @Test
    void storesAndLoadsAnExperimentRoundTrippingItsFullShape(@TempDir Path dir) {
        MutationCache cache = new MutationCache(dir.resolve("cache"));
        CommitPair pair = pair();
        MutationCandidate candidate = candidate();
        MutationExperiment original = killed(pair, candidate);

        cache.store(original);

        MutationExperiment loaded = cache.load(pair, candidate).orElseThrow();
        assertEquals(MutationStatus.KILLED, loaded.status());
        assertEquals(candidate, loaded.mutation());
        assertEquals(pair.baseCommit(), loaded.historicalPair().baseCommit());
        assertEquals("mutant1", loaded.mutantCommit());
        assertEquals(original.killingTests(), loaded.killingTests());
        assertEquals(original.selectedKillingTests(), loaded.selectedKillingTests());
    }

    @Test
    void loadingAMissingEntryIsEmptyNotAnError(@TempDir Path dir) {
        MutationCache cache = new MutationCache(dir.resolve("cache"));
        assertEquals(Optional.empty(), cache.load(pair(), candidate()));
    }

    @Test
    void keysOnTheMutantIdentitySoADifferentOffsetIsADistinctEntry(@TempDir Path dir) {
        MutationCache cache = new MutationCache(dir.resolve("cache"));
        CommitPair pair = pair();
        MutationCandidate at42 = candidate();
        cache.store(killed(pair, at42));

        MutationCandidate at99 = new MutationCandidate(
                at42.sourcePath(), at42.className(), at42.operator(), 99, at42.original(), at42.replacement());
        // Same pair and file, different token offset — a genuinely different mutant, so a miss.
        assertTrue(cache.load(pair, at42).isPresent());
        assertEquals(Optional.empty(), cache.load(pair, at99));
    }

    @Test
    void aDifferentSkippedTestsConfigurationIsADistinctEntrySoTheSkipFlagStaysHonored(@TempDir Path dir) {
        Path cacheDir = dir.resolve("cache");
        CommitPair pair = pair();
        MutationCandidate candidate = candidate();

        MutationCache withoutSkips = new MutationCache(cacheDir);
        withoutSkips.store(killed(pair, candidate));
        assertTrue(withoutSkips.load(pair, candidate).isPresent());

        // A run with --skipped-tests must not silently reuse an experiment computed without it —
        // the skip list changes which tests actually run, so it must change the cache key too.
        MutationCache withSkips = new MutationCache(
                cacheDir, SkippedTests.parse(List.of("com.example.SlowTest")));
        assertEquals(Optional.empty(), withSkips.load(pair, candidate));
    }

    @Test
    void aCorruptCacheFileIsTreatedAsAMissSoTheMutantCanRerun(@TempDir Path dir) throws Exception {
        Path cacheDir = dir.resolve("cache");
        MutationCache cache = new MutationCache(cacheDir);
        CommitPair pair = pair();
        MutationCandidate candidate = candidate();
        cache.store(killed(pair, candidate));

        // Simulate a crash mid-write despite the atomic move: overwrite the one entry with garbage.
        Path only;
        try (var files = Files.list(cacheDir)) {
            only = files.findFirst().orElseThrow();
        }
        Files.writeString(only, "{ this is not valid json");

        assertEquals(Optional.empty(), cache.load(pair, candidate));
    }

    @Test
    void refusesToCacheAnUnbuildableMutant(@TempDir Path dir) {
        MutationCache cache = new MutationCache(dir.resolve("cache"));
        CommitPair pair = pair();
        MutationCandidate candidate = candidate();
        MutationExperiment unbuildable = new MutationExperiment(pair, candidate,
                MutationCandidateOrigin.DIFF_TARGETED, "mutant1",
                MutationStatus.UNBUILDABLE, "mutant failed to build (exit 1)",
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> cache.store(unbuildable));
        assertFalse(cache.load(pair, candidate).isPresent());
    }
}
