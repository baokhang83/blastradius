package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.DependencyRecordSet;
import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.validator.cli.ProgressLogger;
import io.github.baokhang83.blastradius.validator.build.CommitBuildService.BuildKey;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommitBuildServiceTest {

    private final ProgressLogger silent = ProgressLogger.silent();

    private CheckoutPool poolOf(Path tempDir, int size) {
        Path project = tempDir.resolve("project");
        FixtureProjectBuilder.twoModuleReactor(project).commit("initial");
        return CheckoutPool.of(project, tempDir.resolve("scratch"), size);
    }

    private static BuildCache cacheIn(Path tempDir) {
        return new BuildCache(tempDir.resolve("build-cache"));
    }

    private static CommitBuild ok(String sha) {
        return new CommitBuild(false, null, null, List.of());
    }

    @Test
    void buildsEveryDistinctJobAndReturnsThemKeyed(@TempDir Path tempDir) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cacheIn(tempDir), (checkout, sha, agent) -> ok(sha));

            Map<BuildKey, BuildOutcome> built = service.buildAll(List.of(
                    new BuildKey("aaa", true),
                    new BuildKey("bbb", false)));

            assertEquals(2, built.size());
            assertTrue(built.containsKey(new BuildKey("aaa", true)));
            assertTrue(built.containsKey(new BuildKey("bbb", false)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void deduplicatesRepeatedKeysSoACommitIsBuiltOnce(@TempDir Path tempDir) throws Exception {
        AtomicInteger buildCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cacheIn(tempDir), (checkout, sha, agent) -> {
                        buildCount.incrementAndGet();
                        return ok(sha);
                    });

            // The same (sha, agent) appears three times (as happens when consecutive pairs
            // share a commit in fast mode) — it must be built exactly once.
            service.buildAll(List.of(
                    new BuildKey("dup", true),
                    new BuildKey("dup", true),
                    new BuildKey("dup", true)));

            assertEquals(1, buildCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sameShaWithAndWithoutAgentAreDistinctBuilds(@TempDir Path tempDir) throws Exception {
        AtomicInteger buildCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cacheIn(tempDir), (checkout, sha, agent) -> {
                        buildCount.incrementAndGet();
                        return ok(sha);
                    });

            // Safe mode: an internal commit is base-with-agent for one pair and
            // head-without-agent for the next — two genuinely different builds (§III).
            service.buildAll(List.of(
                    new BuildKey("sha", true),
                    new BuildKey("sha", false)));

            assertEquals(2, buildCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void neverRunsMoreConcurrentBuildsThanThePoolHasClones(@TempDir Path tempDir) throws Exception {
        int poolSize = 2;
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try (CheckoutPool pool = poolOf(tempDir, poolSize)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cacheIn(tempDir), (checkout, sha, agent) -> {
                        int now = inFlight.incrementAndGet();
                        maxObserved.accumulateAndGet(now, Math::max);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        inFlight.decrementAndGet();
                        return ok(sha);
                    });

            service.buildAll(List.of(
                    new BuildKey("a", true), new BuildKey("b", true),
                    new BuildKey("c", true), new BuildKey("d", true),
                    new BuildKey("e", true), new BuildKey("f", true)));

            // The pool is the throttle: at most poolSize builds ever run at once, even
            // though the executor has more threads and there are more jobs.
            assertTrue(maxObserved.get() <= poolSize,
                    "observed " + maxObserved.get() + " concurrent builds, pool size " + poolSize);
            // And it actually parallelized — more than one at a time.
            assertTrue(maxObserved.get() >= 2, "builds did not run concurrently at all");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void everyBorrowedCloneIsReleasedSoLaterJobsCanReuseIt(@TempDir Path tempDir) throws Exception {
        Set<Object> distinctCheckouts = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try (CheckoutPool pool = poolOf(tempDir, 1)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cacheIn(tempDir), (checkout, sha, agent) -> {
                        distinctCheckouts.add(checkout);
                        return ok(sha);
                    });

            // Pool of 1, three jobs: each must borrow+release the single clone in turn.
            // If release were broken, the 2nd job would block forever and the test would hang.
            Map<BuildKey, BuildOutcome> built = service.buildAll(List.of(
                    new BuildKey("a", true), new BuildKey("b", true), new BuildKey("c", true)));

            assertEquals(3, built.size());
            assertEquals(1, distinctCheckouts.size(), "pool of 1 must reuse the same clone");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aFailedBuildIsReturnedNotThrownSoOtherCommitsStillComplete(@TempDir Path tempDir) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cacheIn(tempDir), (checkout, sha, agent) ->
                            sha.equals("broken")
                                    ? new CommitBuild(true, "commit broken failed to build", null, List.of())
                                    : ok(sha));

            Map<BuildKey, BuildOutcome> built = service.buildAll(List.of(
                    new BuildKey("broken", true), new BuildKey("fine", true)));

            assertTrue(built.get(new BuildKey("broken", true)).failed());
            assertFalse(built.get(new BuildKey("fine", true)).failed());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aSuccessfulBuildIsWrittenToTheCacheWhileAFailedOneIsNot(@TempDir Path tempDir) throws Exception {
        BuildCache cache = cacheIn(tempDir);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cache, (checkout, sha, agent) ->
                            sha.equals("bad")
                                    ? new CommitBuild(true, "commit bad failed to build", null, List.of())
                                    : ok(sha));

            service.buildAll(List.of(new BuildKey("good", true), new BuildKey("bad", true)));

            // The success is persisted so a later run can resume from it; the failure is not
            // (it is transient and cheap to recompute — caching it would poison every resume).
            assertTrue(cache.contains(new BuildKey("good", true)), "successful build should be cached");
            assertFalse(cache.contains(new BuildKey("bad", true)), "failed build must not be cached");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aCommitAlreadyInTheCacheIsSkippedWithoutRebuilding(@TempDir Path tempDir) throws Exception {
        BuildCache cache = cacheIn(tempDir);
        // Pre-seed the cache as a prior, since-crashed run would have left it.
        cache.store(new BuildKey("resumed", true),
                CommitBuild.succeeded(new DependencyRecordSet(Map.of(), Set.of()), List.of()));

        AtomicInteger buildCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cache, (checkout, sha, agent) -> {
                        buildCount.incrementAndGet();
                        return ok(sha);
                    });

            Map<BuildKey, BuildOutcome> built = service.buildAll(List.of(
                    new BuildKey("resumed", true), new BuildKey("fresh", true)));

            // "resumed" is served from disk (no build); only "fresh" actually runs — the core
            // of resume-after-crash. Both still report a successful outcome to the caller.
            assertEquals(1, buildCount.get(), "the cached commit must not be rebuilt");
            assertFalse(built.get(new BuildKey("resumed", true)).failed());
            assertFalse(built.get(new BuildKey("fresh", true)).failed());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aTransientNetworkFailureIsRetriedAndCanSucceed(@TempDir Path tempDir) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cacheIn(tempDir), (checkout, sha, agent) ->
                            attempts.incrementAndGet() == 1
                                    ? new CommitBuild(true,
                                            "Could not transfer artifact from/to central: Network is unreachable",
                                            null, List.of())
                                    : ok(sha),
                    1);

            Map<BuildKey, BuildOutcome> built = service.buildAll(List.of(new BuildKey("flaky", true)));

            assertFalse(built.get(new BuildKey("flaky", true)).failed());
            assertEquals(2, attempts.get(), "should have retried exactly once before succeeding");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aPersistentTransientFailureGivesUpAfterMaxRetries(@TempDir Path tempDir) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cacheIn(tempDir), (checkout, sha, agent) -> {
                        attempts.incrementAndGet();
                        return new CommitBuild(true, "Connection timed out", null, List.of());
                    },
                    1);

            Map<BuildKey, BuildOutcome> built = service.buildAll(List.of(new BuildKey("alwaysDown", true)));

            assertTrue(built.get(new BuildKey("alwaysDown", true)).failed());
            // Initial attempt + MAX_TRANSIENT_RETRIES retries, then give up.
            assertEquals(4, attempts.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aNonTransientFailureIsNotRetried(@TempDir Path tempDir) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cacheIn(tempDir), (checkout, sha, agent) -> {
                        attempts.incrementAndGet();
                        return new CommitBuild(true, "compilation error: cannot find symbol", null, List.of());
                    });

            Map<BuildKey, BuildOutcome> built = service.buildAll(List.of(new BuildKey("brokenCode", true)));

            assertTrue(built.get(new BuildKey("brokenCode", true)).failed());
            assertEquals(1, attempts.get(), "a real compile failure must not be retried");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aBuilderThatThrowsBecomesAFailedResultNotALostJob(@TempDir Path tempDir) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, cacheIn(tempDir), (checkout, sha, agent) -> {
                        if (sha.equals("boom")) {
                            throw new RuntimeException("kaboom");
                        }
                        return ok(sha);
                    });

            Map<BuildKey, BuildOutcome> built = service.buildAll(List.of(
                    new BuildKey("boom", true), new BuildKey("fine", true)));

            // An unexpected exception in one build must not sink the whole run: it is
            // recorded as a failed CommitBuild (that pair gets excluded downstream).
            assertTrue(built.get(new BuildKey("boom", true)).failed());
            assertTrue(built.get(new BuildKey("boom", true)).failureReason().contains("kaboom"));
            assertFalse(built.get(new BuildKey("fine", true)).failed());
        } finally {
            executor.shutdownNow();
        }
    }
}
