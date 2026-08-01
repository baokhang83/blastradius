package io.github.baokhang83.blastradius.validator.mutation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Enumerates the first, deliberately narrow mutation corpus from {@code src/main/java}.
 * Comments, string literals, character literals, and text blocks are skipped so candidates
 * describe executable Java tokens rather than explanatory text.
 */
public final class MutationCandidateGenerator {

    private static final Path PRODUCTION_SOURCES = Path.of("src", "main", "java");

    /**
     * @param projectRoot Maven project root containing production Java sources
     * @param classFilter exact fully-qualified class name to include, or {@code null} for all
     * @param maxMutations upper bound after deterministic ordering
     */
    public List<MutationCandidate> generate(Path projectRoot, String classFilter, int maxMutations) {
        return generate(projectRoot, classFilter, Integer.MAX_VALUE, maxMutations);
    }

    /** Like {@link #generate(Path, String, int)}, with a bound on production classes visited. */
    public List<MutationCandidate> generate(Path projectRoot, String classFilter, int maxClasses, int maxMutations) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        if (maxClasses < 1) {
            throw new IllegalArgumentException("maxClasses must be positive, got: " + maxClasses);
        }
        if (maxMutations < 1) {
            throw new IllegalArgumentException("maxMutations must be positive, got: " + maxMutations);
        }
        Path sourceRoot = projectRoot.resolve(PRODUCTION_SOURCES);
        if (Files.notExists(sourceRoot)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .filter(path -> classFilter == null || classFilter.equals(className(sourceRoot, path)))
                    .limit(maxClasses)
                    .flatMap(path -> candidatesIn(sourceRoot, path, classFilter).stream())
                    .sorted(Comparator.comparing(MutationCandidate::sourcePath)
                            .thenComparingInt(MutationCandidate::offset))
                    .limit(maxMutations)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to enumerate production Java sources under " + sourceRoot, e);
        }
    }

    private static List<MutationCandidate> candidatesIn(Path sourceRoot, Path sourceFile, String classFilter) {
        String sourcePath = PRODUCTION_SOURCES.resolve(sourceRoot.relativize(sourceFile)).toString()
                .replace(sourceFile.getFileSystem().getSeparator(), "/");
        String className = className(sourceRoot, sourceFile);
        if (classFilter != null && !classFilter.equals(className)) {
            return List.of();
        }
        try {
            return scan(sourcePath, className, Files.readString(sourceFile));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + sourceFile, e);
        }
    }

    private static String className(Path sourceRoot, Path sourceFile) {
        return sourceRoot.relativize(sourceFile).toString()
                .replace(sourceFile.getFileSystem().getSeparator(), ".")
                .replaceFirst("\\.java$", "");
    }

    private static List<MutationCandidate> scan(String sourcePath, String className, String source) {
        List<MutationCandidate> candidates = new ArrayList<>();
        ScanState state = ScanState.CODE;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (state == ScanState.LINE_COMMENT) {
                if (current == '\n' || current == '\r') {
                    state = ScanState.CODE;
                }
                continue;
            }
            if (state == ScanState.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    state = ScanState.CODE;
                    index++;
                }
                continue;
            }
            if (state == ScanState.STRING || state == ScanState.CHARACTER) {
                if (current == '\\') {
                    index++;
                } else if ((state == ScanState.STRING && current == '"')
                        || (state == ScanState.CHARACTER && current == '\'')) {
                    state = ScanState.CODE;
                }
                continue;
            }
            if (state == ScanState.TEXT_BLOCK) {
                if (startsWith(source, index, "\"\"\"")) {
                    state = ScanState.CODE;
                    index += 2;
                }
                continue;
            }
            if (current == '/' && next == '/') {
                state = ScanState.LINE_COMMENT;
                index++;
                continue;
            }
            if (current == '/' && next == '*') {
                state = ScanState.BLOCK_COMMENT;
                index++;
                continue;
            }
            if (startsWith(source, index, "\"\"\"")) {
                state = ScanState.TEXT_BLOCK;
                index += 2;
                continue;
            }
            if (current == '"') {
                state = ScanState.STRING;
                continue;
            }
            if (current == '\'') {
                state = ScanState.CHARACTER;
                continue;
            }
            if (isWordAt(source, index, "true")) {
                candidates.add(candidate(sourcePath, className, MutationOperator.BOOLEAN_LITERAL, index, "true", "false"));
                index += "true".length() - 1;
                continue;
            }
            if (isWordAt(source, index, "false")) {
                candidates.add(candidate(sourcePath, className, MutationOperator.BOOLEAN_LITERAL, index, "false", "true"));
                index += "false".length() - 1;
                continue;
            }
            if (startsWith(source, index, "==")) {
                candidates.add(candidate(sourcePath, className, MutationOperator.EQUALITY_OPERATOR, index, "==", "!="));
                index++;
                continue;
            }
            if (startsWith(source, index, "!=")) {
                candidates.add(candidate(sourcePath, className, MutationOperator.EQUALITY_OPERATOR, index, "!=", "=="));
                index++;
                continue;
            }
            if (startsWith(source, index, ">=")) {
                candidates.add(candidate(sourcePath, className, MutationOperator.RELATIONAL_OPERATOR, index, ">=", "<"));
                index++;
                continue;
            }
            if (startsWith(source, index, "<=")) {
                candidates.add(candidate(sourcePath, className, MutationOperator.RELATIONAL_OPERATOR, index, "<=", ">"));
                index++;
            }
        }
        return candidates;
    }

    private static MutationCandidate candidate(
            String sourcePath, String className, MutationOperator operator, int offset, String original, String replacement) {
        return new MutationCandidate(sourcePath, className, operator, offset, original, replacement);
    }

    private static boolean startsWith(String source, int offset, String token) {
        return source.regionMatches(offset, token, 0, token.length());
    }

    private static boolean isWordAt(String source, int offset, String word) {
        int end = offset + word.length();
        return startsWith(source, offset, word)
                && (offset == 0 || !Character.isJavaIdentifierPart(source.charAt(offset - 1)))
                && (end == source.length() || !Character.isJavaIdentifierPart(source.charAt(end)));
    }

    private enum ScanState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }
}
