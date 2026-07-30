package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static CommitBuild ok(String sha) {
        return new CommitBuild(false, null, null, List.of());
    }

    @Test
    void buildsEveryDistinctJobAndReturnsThemKeyed(@TempDir Path tempDir) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, (checkout, sha, agent) -> ok(sha));

            Map<BuildKey, CommitBuild> built = service.buildAll(List.of(
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
                    pool, executor, silent, (checkout, sha, agent) -> {
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
                    pool, executor, silent, (checkout, sha, agent) -> {
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
                    pool, executor, silent, (checkout, sha, agent) -> {
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
                    pool, executor, silent, (checkout, sha, agent) -> {
                        distinctCheckouts.add(checkout);
                        return ok(sha);
                    });

            // Pool of 1, three jobs: each must borrow+release the single clone in turn.
            // If release were broken, the 2nd job would block forever and the test would hang.
            Map<BuildKey, CommitBuild> built = service.buildAll(List.of(
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
                    pool, executor, silent, (checkout, sha, agent) ->
                            sha.equals("broken")
                                    ? new CommitBuild(true, "commit broken failed to build", null, List.of())
                                    : ok(sha));

            Map<BuildKey, CommitBuild> built = service.buildAll(List.of(
                    new BuildKey("broken", true), new BuildKey("fine", true)));

            assertTrue(built.get(new BuildKey("broken", true)).failed());
            assertFalse(built.get(new BuildKey("fine", true)).failed());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aBuilderThatThrowsBecomesAFailedResultNotALostJob(@TempDir Path tempDir) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (CheckoutPool pool = poolOf(tempDir, 2)) {
            CommitBuildService service = new CommitBuildService(
                    pool, executor, silent, (checkout, sha, agent) -> {
                        if (sha.equals("boom")) {
                            throw new RuntimeException("kaboom");
                        }
                        return ok(sha);
                    });

            Map<BuildKey, CommitBuild> built = service.buildAll(List.of(
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
