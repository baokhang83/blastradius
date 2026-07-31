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
 * fails, or a builder that throws, becomes a {@linkplain BuildOutcome#failed(String) failed}
 * outcome for that one key rather than sinking the whole phase (FR-009).
 *
 * <p><b>Results are not returned in memory.</b> Each successful build is written to the
 * {@link BuildCache} and this returns only a lightweight {@link BuildOutcome} per key — so
 * phase 1's heap stays bounded by the builds in flight rather than growing with the whole
 * window. The cache is also consulted <em>before</em> a clone is borrowed: a key already on
 * disk (from a prior run that died partway) is skipped entirely — no checkout, no {@code mvn}.
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
    private final BuildCache cache;
    private final CommitBuilder builder;

    public CommitBuildService(
            CheckoutPool pool, ExecutorService executor, ProgressLogger progress,
            BuildCache cache, CommitBuilder builder) {
        this.pool = pool;
        this.executor = executor;
        this.progress = progress;
        this.cache = cache;
        this.builder = builder;
    }

    /**
     * Builds every distinct key concurrently and returns a map from key to its lightweight
     * outcome; each success's full result is in the {@link BuildCache}. Duplicate keys are
     * collapsed, so each {@code (sha, agentAttached)} is built (or found cached) exactly once
     * even if several pairs reference it. Blocks until all builds have completed.
     */
    public Map<BuildKey, BuildOutcome> buildAll(List<BuildKey> keys) throws InterruptedException {
        // LinkedHashMap keyed by BuildKey both deduplicates (a repeated key maps to the same
        // future) and preserves the caller's ordering for deterministic submission.
        Map<BuildKey, Future<BuildOutcome>> futures = new LinkedHashMap<>();
        for (BuildKey key : keys) {
            futures.computeIfAbsent(key, k -> executor.submit(() -> buildOne(k)));
        }

        Map<BuildKey, BuildOutcome> results = new ConcurrentHashMap<>();
        try {
            for (Map.Entry<BuildKey, Future<BuildOutcome>> entry : futures.entrySet()) {
                results.put(entry.getKey(), entry.getValue().get());
            }
        } catch (ExecutionException e) {
            // buildOne never throws — it converts every builder failure into a failed
            // BuildOutcome — so an ExecutionException here can only be an unchecked error in
            // the orchestration itself, which should abort the phase rather than be masked.
            throw new IllegalStateException("unexpected failure while building commits", e.getCause());
        }
        return results;
    }

    private BuildOutcome buildOne(BuildKey key) throws InterruptedException {
        String role = key.agentAttached() ? "with agent" : "no agent";
        // Resume: a build already cached from a prior run is reused without borrowing a clone
        // or running mvn — the "if a pair result is found, skip it" path.
        if (cache.contains(key)) {
            progress.buildCached(key.sha(), role);
            return BuildOutcome.ok();
        }
        progress.buildStarted(key.sha(), role);
        long start = System.currentTimeMillis();
        CommitCheckout checkout = pool.borrow();
        try {
            CommitBuild build = builder.build(checkout, key.sha(), key.agentAttached());
            long millis = System.currentTimeMillis() - start;
            if (build.failed()) {
                // Failures are intentionally not cached (transient, cheap to recompute); the
                // referencing pair is excluded, and a re-run rebuilds this commit.
                progress.buildFailed(key.sha(), role, build.failureReason());
                return BuildOutcome.failed(build.failureReason());
            }
            // Persist the heavy payload to disk, then let it become garbage: phase 1 keeps only
            // the lightweight outcome, so heap no longer grows with the window.
            cache.store(key, build);
            progress.buildFinished(key.sha(), role, millis);
            return BuildOutcome.ok();
        } catch (RuntimeException e) {
            // A builder that throws (e.g. an I/O error materializing the checkout) must not
            // lose the job or crash the phase: record it as a failed outcome so the referencing
            // pair is excluded, and let every other commit finish (FR-009).
            String reason = "commit " + key.sha() + " build error: " + e.getMessage();
            progress.buildFailed(key.sha(), role, reason);
            return BuildOutcome.failed(reason);
        } finally {
            pool.release(checkout);
        }
    }
}
