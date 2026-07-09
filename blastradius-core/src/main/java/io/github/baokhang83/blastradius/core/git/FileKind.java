package io.github.baokhang83.blastradius.core.git;

/**
 * Whether a changed file is Java source (subject to dependency-match selection) or
 * something else (subject to the conservative fallback rule, per FR-006).
 */
public enum FileKind {
    JAVA_SOURCE,
    NON_SOURCE
}
