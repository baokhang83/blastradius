package io.github.baokhang83.blastradius.validator.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecord;
import io.github.baokhang83.blastradius.core.tracking.DependencyRecordSet;
import io.github.baokhang83.blastradius.validator.build.CommitBuildService.BuildKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A disk-backed cache of {@linkplain CommitBuild successful commit builds}, keyed by
 * {@link BuildKey} ({@code sha} + {@code agentAttached}). It serves two ends at once:
 *
 * <ul>
 *   <li><b>Bounds memory.</b> A commit's build carries a {@link DependencyRecordSet} (every
 *       test's full class-dependency set) plus its ground-truth results — MBs per commit. Phase 1
 *       used to hold every one of them in a single map for the whole run, so heap grew linearly
 *       with the window and a large {@code --commits} exhausted it mid-build. Writing each build
 *       here and evicting it from memory bounds the live set to what is actually in flight.</li>
 *   <li><b>Enables recovery.</b> A build already on disk is skipped on a re-run — "if a pair
 *       result is found, skip it" — so a run that dies partway (an OOM, a killed terminal) resumes
 *       from where it stopped instead of rebuilding hundreds of commits.</li>
 * </ul>
 *
 * <p><b>Only successful builds are cached</b> (see {@link #store}). A failure carries only a
 * reason string — no memory pressure — recomputes fast (a compile failure fails in seconds), and
 * caching a <em>transient</em> failure (e.g. an executor-shutdown interruption) would poison every
 * later resume by permanently excluding a pair that would build fine. Successes are the expensive,
 * deterministic thing worth persisting.
 *
 * <p><b>Cache validity is the operator's responsibility.</b> The key is only (sha, agentAttached);
 * it does not fingerprint the target repository or this tool's version. A build is deterministic
 * given its sha (same source) and {@code --skip-build-extras} is soundness-neutral, so within one
 * target these entries are sound — but if you point the run at a different repository or upgrade
 * the tracking agent, clear the cache directory first.
 */
public final class BuildCache {

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    public BuildCache(Path directory) {
        this.directory = directory;
    }

    /** True when a successful build for this key is already on disk and can be reused. */
    public boolean contains(BuildKey key) {
        return Files.isRegularFile(fileFor(key));
    }

    /**
     * Reads back a previously {@linkplain #store stored} build, or {@link Optional#empty()} if
     * none is cached — or if the cached file is unreadable/corrupt (a crash could, absent the
     * atomic write below, have left a truncated file). Treating a corrupt entry as a miss lets
     * the caller rebuild it rather than crash the run on a bad cache.
     */
    public Optional<CommitBuild> load(BuildKey key) {
        Path file = fileFor(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            CachedBuild dto = mapper.readValue(file.toFile(), CachedBuild.class);
            return Optional.of(fromDto(dto));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Persists a successful build under its key. Written atomically — to a temp file, then moved
     * into place — so a crash mid-write can never leave a half-written file that a later resume
     * would read as valid ground truth. Rejects a failed build: failures are intentionally not
     * cached (see the class note).
     */
    public void store(BuildKey key, CommitBuild build) {
        if (build.failed()) {
            throw new IllegalArgumentException("only successful builds are cached; refusing to cache a failed build");
        }
        try {
            Files.createDirectories(directory);
            Path tmp = Files.createTempFile(directory, "build-", ".json.tmp");
            mapper.writeValue(tmp.toFile(), toDto(build));
            moveIntoPlace(tmp, fileFor(key));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write build cache entry for " + key.sha(), e);
        }
    }

    private static void moveIntoPlace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some filesystems can't do an atomic move; a plain replace is the best available.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path fileFor(BuildKey key) {
        return directory.resolve(key.sha() + (key.agentAttached() ? "-agent" : "-noagent") + ".json");
    }

    private static CachedBuild toDto(CommitBuild build) {
        DependencyRecordSet set = build.dependencyRecordSet();
        boolean hasDependencies = set != null;
        List<DependencyRecord> dependencies = hasDependencies
                ? set.tests().entrySet().stream()
                        .map(e -> new DependencyRecord(e.getKey(), e.getValue()))
                        .toList()
                : null;
        Set<String> ambient = hasDependencies ? set.ambientDependencies() : null;
        return new CachedBuild(
                build.failed(), build.failureReason(), hasDependencies, dependencies, ambient, build.groundTruth());
    }

    private static CommitBuild fromDto(CachedBuild dto) {
        DependencyRecordSet set = dto.hasDependencies()
                ? new DependencyRecordSet(
                        dto.dependencies().stream()
                                .collect(Collectors.toUnmodifiableMap(
                                        DependencyRecord::test, DependencyRecord::dependencies)),
                        dto.ambientDependencies())
                : null;
        List<GroundTruthResult> groundTruth = dto.groundTruth() == null ? List.of() : dto.groundTruth();
        return new CommitBuild(dto.failed(), dto.failureReason(), set, groundTruth);
    }

    /**
     * On-disk shape of a cached build. The per-test dependency map is flattened to a
     * {@code List<DependencyRecord>} — as {@link io.github.baokhang83.blastradius.core.tracking.DependencyRecordReader}
     * does — because its {@link io.github.baokhang83.blastradius.core.tracking.TestIdentity} key can't be a JSON
     * object key without a custom Jackson key serializer. {@code hasDependencies} distinguishes an
     * agent-free ground-truth build (null {@link DependencyRecordSet}) from an agent build that
     * merely recorded no tests (empty set).
     */
    record CachedBuild(
            boolean failed,
            String failureReason,
            boolean hasDependencies,
            List<DependencyRecord> dependencies,
            Set<String> ambientDependencies,
            List<GroundTruthResult> groundTruth) {}
}
