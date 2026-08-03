package io.github.baokhang83.blastradius.validator.mutation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.baokhang83.blastradius.validator.build.SkippedTests;
import io.github.baokhang83.blastradius.validator.git.CommitPair;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * A disk-backed cache of completed {@linkplain MutationExperiment mutant experiments}, the phase-2
 * analogue of {@link io.github.baokhang83.blastradius.validator.build.BuildCache}. It serves the
 * same two ends for the expensive part of phase 2 — the per-mutant {@code -pl/-am/-amd} Maven build
 * inside {@link HistoricalMutationValidator#validate}:
 *
 * <ul>
 *   <li><b>Enables recovery.</b> A single {@code -amd} pair can run for the better part of an hour;
 *       a run killed partway through phase 2 (an OOM, a killed terminal) used to re-run every
 *       mutant it had already validated. An experiment already on disk is returned straight from
 *       the cache, so a resumed run picks up from the mutant it died on instead of the first.</li>
 *   <li><b>Bounds memory.</b> Writing each experiment here and letting the caller add it to the
 *       report as it goes keeps the live set to what is actually in flight, rather than holding
 *       every mutant's full test-identity lists resident for the whole window.</li>
 * </ul>
 *
 * <p><b>The cache key is the mutant's identity plus whatever changes its build outcome.</b> A
 * mutant is fully described by its pair ({@code base -> head}) and its single-token source edit
 * ({@code sourcePath}, {@code offset}, {@code operator}, {@code original}, {@code replacement});
 * given those <em>and</em> the set of {@link SkippedTests} excluded from every target build, its
 * build outcome and selection comparison are deterministic. {@code --max-mutations-per-pair} and
 * the class filter change <em>which</em> candidates are generated, not the outcome of any one of
 * them — so they are deliberately kept out of the key, which maximises resume hits (a re-run with
 * a wider bound still reuses every mutant the narrower run already validated). {@code
 * --skipped-tests} is different: it changes which tests actually run in the mutant's build, so it
 * changes {@code killingTests}/{@code selectedKillingTests}/{@code skippedTests} on the resulting
 * experiment — omitting it from the key would let a run with a different skip list silently reuse
 * an experiment computed under the old one, making {@code --skipped-tests} appear to stop applying
 * once entries exist on disk. It is folded into the key precisely so that changing it invalidates
 * the affected entries instead of serving stale ones.
 *
 * <p><b>Only compilable outcomes are cached</b> (see {@link #store}). A {@code KILLED}/{@code
 * SURVIVED} experiment is the deterministic result of a build that ran to completion; an {@code
 * UNBUILDABLE} one is phase 2's analogue of a failed build — it recomputes fast (a compile failure
 * fails in seconds) and could in principle reflect a transient build interruption, which cached
 * would poison every later resume. Mirrors {@code BuildCache}'s "only successful builds" stance.
 *
 * <p><b>Cache validity is the operator's responsibility</b>, exactly as for {@code BuildCache}: the
 * key fingerprints neither the target repository nor this tool's version. Point the run at a
 * different repository or upgrade the tracking agent and you must clear the cache directory first.
 */
public final class MutationCache {

    private final Path directory;
    private final SkippedTests skippedTests;
    private final ObjectMapper mapper = new ObjectMapper();

    public MutationCache(Path directory) {
        this(directory, SkippedTests.none());
    }

    /**
     * @param skippedTests the exclusions applied to every target build for this run — folded into
     *                      the cache key (see the class note) so a run with a different skip list
     *                      never reuses an experiment computed under a different one.
     */
    public MutationCache(Path directory, SkippedTests skippedTests) {
        this.directory = directory;
        this.skippedTests = java.util.Objects.requireNonNull(skippedTests, "skippedTests");
    }

    /**
     * Reads back a previously {@linkplain #store stored} experiment for this candidate on this
     * pair, or {@link Optional#empty()} if none is cached — or if the cached file is
     * unreadable/corrupt (a crash could, absent the atomic write below, have left a truncated
     * file). Treating a corrupt entry as a miss lets the caller re-run the mutant rather than
     * crash the run on a bad cache.
     */
    public Optional<MutationExperiment> load(CommitPair pair, MutationCandidate candidate) {
        Path file = fileFor(pair, candidate);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(file.toFile(), MutationExperiment.class));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Persists a completed experiment under its mutant identity. Written atomically — to a temp
     * file, then moved into place — so a crash mid-write can never leave a half-written file a
     * later resume would read as valid evidence. Rejects an {@code UNBUILDABLE} experiment:
     * unbuildable mutants are intentionally not cached (see the class note).
     */
    public void store(MutationExperiment experiment) {
        if (experiment.status() == MutationStatus.UNBUILDABLE) {
            throw new IllegalArgumentException(
                    "only compilable experiments are cached; refusing to cache an unbuildable mutant");
        }
        try {
            Files.createDirectories(directory);
            Path tmp = Files.createTempFile(directory, "mutant-", ".json.tmp");
            mapper.writeValue(tmp.toFile(), experiment);
            moveIntoPlace(tmp, fileFor(experiment.historicalPair(), experiment.mutation()));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write mutation cache entry", e);
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

    /**
     * A collision-resistant filename derived from the mutant's full identity. The parts contain
     * slashes and arbitrary source tokens, so they are hashed rather than used as a path; a NUL
     * separator keeps the fields unambiguous (no source token or path contains a NUL byte).
     */
    private Path fileFor(CommitPair pair, MutationCandidate candidate) {
        String identity = String.join("\0",
                pair.baseCommit(), pair.headCommit(), candidate.sourcePath(),
                Integer.toString(candidate.offset()), candidate.operator().name(),
                candidate.original(), candidate.replacement(),
                String.join(",", skippedTests.classes()));
        return directory.resolve(sha256Hex(identity) + ".json");
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
