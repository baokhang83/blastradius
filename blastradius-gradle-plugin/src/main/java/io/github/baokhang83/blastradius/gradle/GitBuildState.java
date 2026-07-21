package io.github.baokhang83.blastradius.gradle;

import java.io.Serializable;

/** Immutable Git state captured during Gradle configuration and used as a task input. */
record GitBuildState(String headCommit, String baseReferenceCommit, String comparisonBaseCommit) implements Serializable {

    boolean baseReferenceBuild() {
        return headCommit.equals(baseReferenceCommit);
    }

    boolean comparisonBaseAvailable() {
        return comparisonBaseCommit != null;
    }
}
