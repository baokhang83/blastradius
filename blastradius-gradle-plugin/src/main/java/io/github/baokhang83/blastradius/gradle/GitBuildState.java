package io.github.baokhang83.blastradius.gradle;

import java.io.Serializable;

/** Immutable Git state captured during Gradle configuration and used as a task input. */
record GitBuildState(String headCommit, String baseCommit) implements Serializable {

    boolean baseReferenceBuild() {
        return headCommit.equals(baseCommit);
    }
}
