package io.github.baokhang83.blastradius.core.git;

/**
 * How a changed file is treated by selection:
 * <ul>
 *   <li>{@link #JAVA_SOURCE} — JVM source subject to dependency-match selection;
 *       this legacy-named value covers Java and conventional Kotlin source paths;</li>
 *   <li>{@link #NON_SOURCE} — subject to the conservative fallback rule (FR-006), because it
 *       may affect a test outcome in a way class-load tracking cannot observe;</li>
 *   <li>{@link #INERT} — provably cannot affect any test outcome (docs, license, images,
 *       VCS/editor/CI metadata), so it selects no tests at all (Constitution §III).</li>
 * </ul>
 */
public enum FileKind {
    JAVA_SOURCE,
    NON_SOURCE,
    INERT
}
