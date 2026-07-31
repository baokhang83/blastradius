package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.DependencyRecordSet;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import io.github.baokhang83.blastradius.validator.build.CommitBuildService.BuildKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildCacheTest {

    @Test
    void storesAndLoadsAnAgentBuildRoundTrippingDependenciesAndGroundTruth(@TempDir Path dir) {
        BuildCache cache = new BuildCache(dir.resolve("cache"));
        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "passes");
        DependencyRecordSet deps = new DependencyRecordSet(
                Map.of(fooTest, Map.of("com.example.Foo", "abc123")),
                Set.of("com.example.Ambient"));
        CommitBuild original = CommitBuild.succeeded(
                deps, List.of(new GroundTruthResult(fooTest, Outcome.PASSED)));

        BuildKey key = new BuildKey("sha1", true);
        cache.store(key, original);

        assertTrue(cache.contains(key));
        CommitBuild loaded = cache.load(key).orElseThrow();
        assertFalse(loaded.failed());
        assertEquals(deps.tests(), loaded.dependencyRecordSet().tests());
        assertEquals(deps.ambientDependencies(), loaded.dependencyRecordSet().ambientDependencies());
        assertEquals(1, loaded.groundTruth().size());
        assertEquals(fooTest, loaded.groundTruth().get(0).test());
        assertEquals(Outcome.PASSED, loaded.groundTruth().get(0).outcome());
    }

    @Test
    void anAgentFreeGroundTruthBuildRoundTripsWithANullDependencySet(@TempDir Path dir) {
        BuildCache cache = new BuildCache(dir.resolve("cache"));
        TestIdentity t = new TestIdentity("com.example.BarTest", "runs");
        // A head build in safe mode has no agent attached, so its dependencyRecordSet is null;
        // that must round-trip as null, not as an empty set (the two mean different things).
        CommitBuild original = new CommitBuild(false, null, null,
                List.of(new GroundTruthResult(t, Outcome.CONFIRMED_FAILED)));

        BuildKey key = new BuildKey("sha2", false);
        cache.store(key, original);

        CommitBuild loaded = cache.load(key).orElseThrow();
        assertNull(loaded.dependencyRecordSet(), "an agent-free build's dependency set must stay null");
        assertEquals(Outcome.CONFIRMED_FAILED, loaded.groundTruth().get(0).outcome());
    }

    @Test
    void distinguishesTheSameShaWithAndWithoutTheAgent(@TempDir Path dir) {
        BuildCache cache = new BuildCache(dir.resolve("cache"));
        cache.store(new BuildKey("sha", true), CommitBuild.succeeded(
                new DependencyRecordSet(Map.of(), Set.of()), List.of()));

        // The no-agent variant of the same sha is a different build (§III) and must be a miss.
        assertTrue(cache.contains(new BuildKey("sha", true)));
        assertFalse(cache.contains(new BuildKey("sha", false)));
    }

    @Test
    void loadingAMissingEntryIsEmptyNotAnError(@TempDir Path dir) {
        BuildCache cache = new BuildCache(dir.resolve("cache"));
        assertFalse(cache.contains(new BuildKey("never", true)));
        assertEquals(Optional.empty(), cache.load(new BuildKey("never", true)));
    }

    @Test
    void aCorruptCacheFileIsTreatedAsAMissSoTheRunCanRebuild(@TempDir Path dir) throws Exception {
        Path cacheDir = dir.resolve("cache");
        BuildCache cache = new BuildCache(cacheDir);
        BuildKey key = new BuildKey("truncated", true);
        cache.store(key, CommitBuild.succeeded(new DependencyRecordSet(Map.of(), Set.of()), List.of()));

        // Simulate a crash mid-write despite the atomic move: overwrite with garbage.
        Files.writeString(cacheDir.resolve("truncated-agent.json"), "{ this is not valid json");

        // A corrupt entry must not crash the run — it reads as a miss so the caller rebuilds.
        assertEquals(Optional.empty(), cache.load(key));
    }

    @Test
    void refusesToCacheAFailedBuild(@TempDir Path dir) {
        BuildCache cache = new BuildCache(dir.resolve("cache"));
        assertThrows(IllegalArgumentException.class,
                () -> cache.store(new BuildKey("bad", true), CommitBuild.failed("boom")));
    }
}
