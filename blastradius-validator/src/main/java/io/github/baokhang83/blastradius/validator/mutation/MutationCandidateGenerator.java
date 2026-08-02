package io.github.baokhang83.blastradius.validator.mutation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Enumerates the first, deliberately narrow mutation corpus from every module's
 * {@code src/main/java} across the whole reactor. Comments, string literals, character literals,
 * and text blocks are skipped so candidates describe executable Java tokens rather than
 * explanatory text.
 */
public final class MutationCandidateGenerator {

    private static final String PRODUCTION_MARKER = "src/main/java/";

    /**
     * @param projectRoot Maven project root containing production Java sources
     * @param classFilter exact fully-qualified class name to include, or {@code null} for all
     * @param maxMutations upper bound after deterministic ordering
     */
    public List<MutationCandidate> generate(Path projectRoot, String classFilter, int maxMutations) {
        return generate(projectRoot, classFilter, Integer.MAX_VALUE, maxMutations);
    }

    /** Like {@link #generate(Path, String, int)}, with a bound on production classes visited. */
    public List<MutationCandidate> generate(
            Path projectRoot, String classFilter, int maxMutationClasses, int maxMutations) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        if (maxMutationClasses < 1) {
            throw new IllegalArgumentException(
                    "maxMutationClasses must be positive, got: " + maxMutationClasses);
        }
        if (maxMutations < 1) {
            throw new IllegalArgumentException("maxMutations must be positive, got: " + maxMutations);
        }
        if (Files.notExists(projectRoot)) {
            return List.of();
        }
        List<String> sources = collectProductionSources(projectRoot).stream()
                .sorted()
                .filter(path -> classFilter == null || classFilter.equals(classNameOf(path)))
                .toList();
        // maxMutationClasses bounds the classes we actually MUTATE, not the files we look at. A
        // source with no mutable token (an abstract class, an interface, a constant-free type) must
        // not consume the budget: applying the limit before scanning meant a reactor whose
        // alphabetically-first source happens to be token-free would yield zero mutants while
        // thousands of mutable classes went unvisited (apache/shenyu: --max-mutation-classes 1
        // landed on a token-free AbstractDataChangedInit and generated nothing).
        List<MutationCandidate> collected = new ArrayList<>();
        int classesMutated = 0;
        for (String path : sources) {
            if (classesMutated >= maxMutationClasses) {
                break;
            }
            List<MutationCandidate> inClass = candidatesIn(projectRoot, path, classFilter);
            if (!inClass.isEmpty()) {
                collected.addAll(inClass);
                classesMutated++;
            }
        }
        return collected.stream()
                .sorted(Comparator.comparing(MutationCandidate::sourcePath)
                        .thenComparingInt(MutationCandidate::offset))
                .limit(maxMutations)
                .toList();
    }

    /**
     * Every {@code .java} production source anywhere in the reactor, as a repo-relative,
     * forward-slashed path. A source counts as production when its path contains a
     * {@value #PRODUCTION_MARKER} segment; this discovers one root per module (a real reactor has
     * no {@code src/main/java} at the repo root — each submodule carries its own), rather than
     * assuming a single root at {@code projectRoot}. {@code .git}, {@code target}, and {@code build}
     * subtrees are pruned so generated output and VCS internals are never mutated.
     */
    private static List<String> collectProductionSources(Path projectRoot) {
        List<String> sources = new ArrayList<>();
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (".git".equals(name) || "target".equals(name) || "build".equals(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String relative = projectRoot.relativize(file).toString().replace('\\', '/');
                    if (relative.endsWith(".java") && relative.contains(PRODUCTION_MARKER)) {
                        sources.add(relative);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to enumerate production Java sources under " + projectRoot, e);
        }
        return sources;
    }

    private static List<MutationCandidate> candidatesIn(Path projectRoot, String repoRelativePath, String classFilter) {
        String className = classNameOf(repoRelativePath);
        if (classFilter != null && !classFilter.equals(className)) {
            return List.of();
        }
        try {
            return scan(repoRelativePath, className, Files.readString(projectRoot.resolve(repoRelativePath)));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + repoRelativePath, e);
        }
    }

    /** Derives the FQN from the path <em>after</em> the last {@value #PRODUCTION_MARKER} segment. */
    private static String classNameOf(String repoRelativePath) {
        int marker = repoRelativePath.lastIndexOf(PRODUCTION_MARKER);
        String afterRoot = marker < 0
                ? repoRelativePath
                : repoRelativePath.substring(marker + PRODUCTION_MARKER.length());
        return afterRoot.replace('/', '.').replaceFirst("\\.java$", "");
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
