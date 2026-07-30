package io.github.baokhang83.blastradius.validator.build;

import io.github.baokhang83.blastradius.validator.cli.ProgressLogger;
import io.github.baokhang83.blastradius.validator.git.CommitCheckout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Phase 1 of a run: builds every commit the window needs, concurrently, across a
 * {@link CheckoutPool} of isolated working copies — the redesign's core lever for getting
 * wall-clock per pair down. A single {@code -T} reactor build leaves cores idle on its
 * critical path; running several commits' builds at once (each in its own clone) fills them.
 *
 * <p>The <em>set</em> of builds is decided by the caller and expressed as {@link BuildKey}s
 * — a {@code (sha, agentAttached)} pair. Two keys with the same sha but different
 * {@code agentAttached} are genuinely different builds (safe mode's base-with-agent vs.
 * head-without-agent, which keeps ground truth independent of the tracking agent — §III),
 * so they are built separately; identical keys (a commit shared by consecutive pairs) are
 * deduplicated and built once.
 *
 * <p>The pool — not the executor — is the concurrency throttle: a worker blocks on
 * {@link CheckoutPool#borrow()} until a clone is free, so at most {@code pool size} builds
 * ever run at once regardless of how many jobs or executor threads exist. A build that
 * fails, or a builder that throws, becomes a {@linkplain CommitBuild#failed(String) failed}
 * result for that one key rather than sinking the whole phase (FR-009).
 */
public final class CommitBuildService {

    /**
     * Identifies a single build the window needs: a commit, built either with the tracking
     * agent attached (to record dependencies) or without it (an independent ground-truth
     * build). Its {@code equals}/{@code hashCode} are what dedupe and memoize builds.
     */
    public record BuildKey(String sha, boolean agentAttached) {}

    /** Performs one commit's build on a borrowed, already-owned checkout. */
    @FunctionalInterface
    public interface CommitBuilder {
        CommitBuild build(CommitCheckout checkout, String sha, boolean agentAttached);
    }

    private final CheckoutPool pool;
    private final ExecutorService executor;
    private final ProgressLogger progress;
    private final CommitBuilder builder;

    public CommitBuildService(
            CheckoutPool pool, ExecutorService executor, ProgressLogger progress, CommitBuilder builder) {
        this.pool = pool;
        this.executor = executor;
        this.progress = progress;
        this.builder = builder;
    }

    /**
     * Builds every distinct key concurrently and returns a map from key to its outcome.
     * Duplicate keys are collapsed, so each {@code (sha, agentAttached)} is built exactly
     * once even if several pairs reference it. Blocks until all builds have completed.
     */
    public Map<BuildKey, CommitBuild> buildAll(List<BuildKey> keys) throws InterruptedException {
        // LinkedHashMap keyed by BuildKey both deduplicates (a repeated key maps to the same
        // future) and preserves the caller's ordering for deterministic submission.
        Map<BuildKey, Future<CommitBuild>> futures = new LinkedHashMap<>();
        for (BuildKey key : keys) {
            futures.computeIfAbsent(key, k -> executor.submit(() -> buildOne(k)));
        }

        Map<BuildKey, CommitBuild> results = new ConcurrentHashMap<>();
        try {
            for (Map.Entry<BuildKey, Future<CommitBuild>> entry : futures.entrySet()) {
                results.put(entry.getKey(), entry.getValue().get());
            }
        } catch (ExecutionException e) {
            // buildOne never throws — it converts every builder failure into a failed
            // CommitBuild — so an ExecutionException here can only be an unchecked error in
            // the orchestration itself, which should abort the phase rather than be masked.
            throw new IllegalStateException("unexpected failure while building commits", e.getCause());
        }
        return results;
    }

    private CommitBuild buildOne(BuildKey key) throws InterruptedException {
        String role = key.agentAttached() ? "with agent" : "no agent";
        progress.buildStarted(key.sha(), role);
        long start = System.currentTimeMillis();
        CommitCheckout checkout = pool.borrow();
        try {
            CommitBuild build = builder.build(checkout, key.sha(), key.agentAttached());
            long millis = System.currentTimeMillis() - start;
            if (build.failed()) {
                progress.buildFailed(key.sha(), role, build.failureReason());
            } else {
                progress.buildFinished(key.sha(), role, millis);
            }
            return build;
        } catch (RuntimeException e) {
            // A builder that throws (e.g. an I/O error materializing the checkout) must not
            // lose the job or crash the phase: record it as a failed build so the referencing
            // pair is excluded, and let every other commit finish (FR-009).
            String reason = "commit " + key.sha() + " build error: " + e.getMessage();
            progress.buildFailed(key.sha(), role, reason);
            return CommitBuild.failed(reason);
        } finally {
            pool.release(checkout);
        }
    }
}
