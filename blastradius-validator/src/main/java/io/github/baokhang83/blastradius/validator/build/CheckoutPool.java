package io.github.baokhang83.blastradius.validator.build;

import io.github.baokhang83.blastradius.validator.git.CommitCheckout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A fixed-size pool of independent {@link CommitCheckout} clones, so up to {@code size}
 * commit builds can run at once without stomping each other's working tree / {@code target/}
 * on disk. Each clone is a full, isolated working copy of the target project (the target
 * repo itself is only ever read from); a build worker {@link #borrow()}s one, checks out the
 * commit it needs, builds, then {@link #release}s it back for the next worker.
 *
 * <p>Reusing whole {@code CommitCheckout} instances (rather than cloning per build) keeps
 * constitution &sect;VII's exhaustive {@code target/} cleanup on every checkout intact, and
 * avoids re-cloning a large repo K times per commit.
 */
public final class CheckoutPool implements AutoCloseable {

    private final List<CommitCheckout> allClones;
    private final BlockingQueue<CommitCheckout> available;
    private volatile boolean closed;

    private CheckoutPool(List<CommitCheckout> clones) {
        this.allClones = clones;
        this.available = new LinkedBlockingQueue<>(clones);
    }

    /**
     * Creates {@code size} independent clones of {@code targetRepoPath} under
     * {@code scratchParentDir}. Cloning is the expensive part and happens once here, up
     * front, not per build.
     */
    public static CheckoutPool of(Path targetRepoPath, Path scratchParentDir, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("pool size must be positive, got: " + size);
        }
        List<CommitCheckout> clones = new ArrayList<>(size);
        try {
            java.nio.file.Files.createDirectories(scratchParentDir);
            for (int i = 0; i < size; i++) {
                clones.add(CommitCheckout.forTargetProject(targetRepoPath, scratchParentDir));
            }
        } catch (RuntimeException | java.io.IOException e) {
            // Clean up any clones already made before rethrowing, so a partial failure
            // doesn't leak scratch working copies.
            clones.forEach(CommitCheckout::close);
            throw e instanceof RuntimeException re
                    ? re
                    : new IllegalStateException("failed to create checkout pool", e);
        }
        return new CheckoutPool(clones);
    }

    /**
     * Takes an idle clone, blocking until one is free if all {@code size} are in use.
     *
     * @throws IllegalStateException if the pool is already closed
     */
    public CommitCheckout borrow() throws InterruptedException {
        if (closed) {
            throw new IllegalStateException("checkout pool is closed");
        }
        return available.take();
    }

    /** Returns a borrowed clone to the pool for the next borrower. */
    public void release(CommitCheckout checkout) {
        if (closed) {
            return;
        }
        available.add(checkout);
    }

    /** Closes and deletes every clone. Idempotent. The target repo is untouched. */
    @Override
    public void close() {
        closed = true;
        allClones.forEach(CommitCheckout::close);
    }
}
