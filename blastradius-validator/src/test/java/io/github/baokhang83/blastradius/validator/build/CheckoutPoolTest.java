package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.validator.git.CommitCheckout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckoutPoolTest {

    @Test
    void borrowGivesEachConcurrentCallerADistinctClone(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("project");
        FixtureProjectBuilder.twoModuleReactor(project).commit("initial");

        try (CheckoutPool pool = CheckoutPool.of(project, tempDir.resolve("scratch"), 3)) {
            CommitCheckout a = pool.borrow();
            CommitCheckout b = pool.borrow();
            CommitCheckout c = pool.borrow();

            // Three concurrent borrowers must get three DIFFERENT working copies, or two
            // builds would stomp each other's target/ on disk.
            assertNotSame(a, b);
            assertNotSame(b, c);
            assertNotSame(a, c);
        }
    }

    @Test
    void aReleasedCloneIsHandedBackOutToTheNextBorrower(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("project");
        FixtureProjectBuilder.twoModuleReactor(project).commit("initial");

        try (CheckoutPool pool = CheckoutPool.of(project, tempDir.resolve("scratch"), 1)) {
            CommitCheckout first = pool.borrow();
            pool.release(first);
            CommitCheckout second = pool.borrow();

            // Pool of 1: the only clone must be reused, not leaked.
            assertEquals(first, second);
        }
    }

    @Test
    void borrowBlocksUntilACloneIsReleasedWhenThePoolIsExhausted(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("project");
        FixtureProjectBuilder.twoModuleReactor(project).commit("initial");

        try (CheckoutPool pool = CheckoutPool.of(project, tempDir.resolve("scratch"), 1)) {
            CommitCheckout held = pool.borrow();

            CountDownLatch acquired = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                try {
                    pool.borrow();
                    acquired.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // With the sole clone still held, the waiter must NOT proceed.
            assertTrue(!acquired.await(300, TimeUnit.MILLISECONDS), "borrow should block while pool is exhausted");

            pool.release(held);
            assertTrue(acquired.await(2, TimeUnit.SECONDS), "borrow should unblock once a clone is released");
            executor.shutdownNow();
        }
    }

    @Test
    void everyCloneIsAnIndependentWorkingCopyOfTheProject(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("project");
        FixtureProjectBuilder.twoModuleReactor(project).commit("initial");

        Set<Path> distinctDirs = ConcurrentHashMap.newKeySet();
        try (CheckoutPool pool = CheckoutPool.of(project, tempDir.resolve("scratch"), 2)) {
            CommitCheckout a = pool.borrow();
            CommitCheckout b = pool.borrow();
            distinctDirs.add(a.checkoutCommit("HEAD"));
            distinctDirs.add(b.checkoutCommit("HEAD"));
        }
        // Two clones -> two distinct on-disk working directories, both real checkouts.
        assertEquals(2, distinctDirs.size());
        assertTrue(distinctDirs.stream().allMatch(d -> Files.isDirectory(d.resolve(".git"))));
    }

    @Test
    void closeIsIdempotentAndDeletesEveryClone(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("project");
        FixtureProjectBuilder.twoModuleReactor(project).commit("initial");
        Path scratch = tempDir.resolve("scratch");

        CheckoutPool pool = CheckoutPool.of(project, scratch, 2);
        pool.close();
        pool.close(); // second close must not throw

        // Borrowing after close is a programming error, surfaced not silently ignored.
        assertThrows(IllegalStateException.class, pool::borrow);
    }

    @Test
    void requiresAPositivePoolSize(@TempDir Path tempDir) {
        Path project = tempDir.resolve("project");
        FixtureProjectBuilder.twoModuleReactor(project).commit("initial");
        assertThrows(IllegalArgumentException.class, () -> CheckoutPool.of(project, tempDir.resolve("scratch"), 0));
    }
}
