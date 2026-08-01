package io.github.baokhang83.blastradius.validator.mutation;

import io.github.baokhang83.blastradius.validator.git.CommitCheckout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** A disposable target-project clone that can turn one candidate into a real child commit. */
public final class SyntheticMutationCheckout implements AutoCloseable {

    private final CommitCheckout checkout;

    private SyntheticMutationCheckout(CommitCheckout checkout) {
        this.checkout = checkout;
    }

    public static SyntheticMutationCheckout forTargetProject(Path targetProject, Path scratchParent) {
        return new SyntheticMutationCheckout(CommitCheckout.forTargetProject(targetProject, scratchParent));
    }

    public Path checkoutBaseline(String baselineSha) {
        return checkout.checkoutCommit(baselineSha);
    }

    /** Applies {@code candidate} and commits the result as a direct child of the current baseline. */
    public String commit(MutationCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        Path source = workTree().resolve(candidate.sourcePath());
        try {
            String mutated = candidate.applyTo(Files.readString(source));
            return checkout.commitFile(Path.of(candidate.sourcePath()), mutated,
                    "blastradius mutation: " + candidate.className() + " " + candidate.operator());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("failed to mutate " + candidate.sourcePath(), e);
        }
    }

    public Path workTree() {
        return checkout.workTree();
    }

    @Override
    public void close() {
        checkout.close();
    }
}
