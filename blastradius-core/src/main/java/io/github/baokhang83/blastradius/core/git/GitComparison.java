package io.github.baokhang83.blastradius.core.git;

import java.util.Objects;
import java.util.Optional;

/** The commits that define one build's comparison with a configured Git reference. */
public record GitComparison(
        String headCommit, String baseReferenceCommit, Optional<String> comparisonBaseCommit) {

    public GitComparison {
        Objects.requireNonNull(headCommit, "headCommit");
        Objects.requireNonNull(baseReferenceCommit, "baseReferenceCommit");
        Objects.requireNonNull(comparisonBaseCommit, "comparisonBaseCommit");
    }

    /** Whether this build is executing the configured base reference itself. */
    public boolean baseReferenceBuild() {
        return headCommit.equals(baseReferenceCommit);
    }
}
