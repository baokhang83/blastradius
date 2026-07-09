package io.github.baokhang83.blastradius.core.git;

import java.util.Objects;

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
}
