package io.github.baokhang83.blastradius.core.git;

import java.util.Objects;
import java.util.Set;

/**
 * One file that differs between a commit pair's base and head.
 *
 * @param path              repo-relative path
 * @param kind              {@link FileKind#JAVA_SOURCE} or {@link FileKind#NON_SOURCE}
 * @param changedClassName  fully-qualified class name; required when {@code kind} is
 *                          {@code JAVA_SOURCE}, must be {@code null} otherwise
 */
public record ChangedFile(String path, FileKind kind, String changedClassName) {

    public ChangedFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        if (kind == FileKind.JAVA_SOURCE) {
            Objects.requireNonNull(changedClassName, "changedClassName required for JAVA_SOURCE");
        }
    }

    /**
     * JVM class names a source change can affect. Kotlin emits a file facade named
     * {@code FileNameKt} for top-level declarations in addition to an ordinary class whose
     * name follows the source file name.
     */
    public Set<String> candidateClassNames() {
        if (kind != FileKind.JAVA_SOURCE) {
            return Set.of();
        }
        if (path.endsWith(".kt")) {
            return Set.of(changedClassName, changedClassName + "Kt");
        }
        return Set.of(changedClassName);
    }
}
