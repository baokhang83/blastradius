package io.github.baokhang83.blastradius.validator.mutation;

import java.util.Objects;

/** One deterministic, single-token source replacement in a production Java class. */
public record MutationCandidate(
        String sourcePath,
        String className,
        MutationOperator operator,
        int offset,
        String original,
        String replacement) {

    public MutationCandidate {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(replacement, "replacement");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative: " + offset);
        }
        if (original.isEmpty() || replacement.isEmpty()) {
            throw new IllegalArgumentException("mutation tokens must not be empty");
        }
    }

    /** Applies this candidate only when its source still contains the recorded token. */
    public String applyTo(String source) {
        Objects.requireNonNull(source, "source");
        int end = offset + original.length();
        if (end > source.length() || !source.regionMatches(offset, original, 0, original.length())) {
            throw new IllegalArgumentException("source no longer matches mutation candidate at offset " + offset);
        }
        return source.substring(0, offset) + replacement + source.substring(end);
    }
}
