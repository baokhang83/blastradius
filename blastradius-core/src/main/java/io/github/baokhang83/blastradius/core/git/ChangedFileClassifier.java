package io.github.baokhang83.blastradius.core.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import java.util.regex.Pattern;

/**
 * Diffs a commit pair and classifies each changed file as {@link FileKind#JAVA_SOURCE}
 * (subject to dependency-match selection) or {@link FileKind#NON_SOURCE} (subject to the
 * conservative fallback rule, per FR-006). Java and conventional Kotlin source class names are
 * derived from standard Maven source layouts, which also makes multi-module reactors
 * module-agnostic here (research.md #5). Kotlin files with an {@code inline fun} on either side
 * of a diff deliberately fall back: the compiler copies their body into callers, so no stable
 * class-load dependency can prove every affected test was selected.
 */
public final class ChangedFileClassifier {

    private static final String MAIN_JAVA_MARKER = "src/main/java/";
    private static final String TEST_JAVA_MARKER = "src/test/java/";
    private static final String MAIN_KOTLIN_MARKER = "src/main/kotlin/";
    private static final String TEST_KOTLIN_MARKER = "src/test/kotlin/";
    private static final Pattern INLINE_FUNCTION = Pattern.compile("\\binline\\b(?:\\s+[A-Za-z]+)*\\s+fun\\b");

    public List<ChangedFile> classify(Path repoPath, String baseCommit, String headCommit) {
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId baseId = repo.resolve(baseCommit);
            ObjectId headId = repo.resolve(headCommit);

            try (RevWalk walk = new RevWalk(repo); ObjectReader reader = repo.newObjectReader()) {
                RevCommit base = walk.parseCommit(baseId);
                RevCommit head = walk.parseCommit(headId);

                CanonicalTreeParser oldTree = new CanonicalTreeParser();
                oldTree.reset(reader, base.getTree());
                CanonicalTreeParser newTree = new CanonicalTreeParser();
                newTree.reset(reader, head.getTree());

                List<DiffEntry> entries = git.diff().setOldTree(oldTree).setNewTree(newTree).call();

                List<ChangedFile> result = new ArrayList<>();
                for (DiffEntry entry : entries) {
                    String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                            ? entry.getOldPath()
                            : entry.getNewPath();
                    result.add(classifyEntry(path, entry, reader));
                }
                return result;
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to classify changes between " + baseCommit + " and " + headCommit, e);
        }
    }

    private static ChangedFile classifyEntry(String path, DiffEntry entry, ObjectReader reader) throws IOException {
        if (path.endsWith(".kt") && containsInlineFunction(entry, reader)) {
            return new ChangedFile(path, FileKind.NON_SOURCE, null);
        }
        String className = classNameFromPath(path);
        if (className != null) {
            return new ChangedFile(path, FileKind.JAVA_SOURCE, className);
        }
        if (isInert(path)) {
            return new ChangedFile(path, FileKind.INERT, null);
        }
        return new ChangedFile(path, FileKind.NON_SOURCE, null);
    }

    private static boolean containsInlineFunction(DiffEntry entry, ObjectReader reader) throws IOException {
        return sourceContainsInlineFunction(entry.getOldId().toObjectId(), reader)
                || sourceContainsInlineFunction(entry.getNewId().toObjectId(), reader);
    }

    private static boolean sourceContainsInlineFunction(ObjectId sourceId, ObjectReader reader) throws IOException {
        if (ObjectId.zeroId().equals(sourceId)) {
            return false;
        }
        String source = new String(reader.open(sourceId).getBytes(), StandardCharsets.UTF_8);
        return INLINE_FUNCTION.matcher(source).find();
    }

    // Documentation and media extensions that cannot affect a test outcome.
    private static final Set<String> INERT_EXTENSIONS = Set.of(
            ".md", ".markdown", ".adoc", ".rst",
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".webp");

    // Root-style documentation files, matched with or without an extension (README, README.md, …).
    private static final Set<String> INERT_DOC_STEMS = Set.of(
            "README", "LICENSE", "NOTICE", "AUTHORS", "CHANGELOG", "CONTRIBUTING",
            "CODE_OF_CONDUCT", "SECURITY");

    // Exact VCS/editor metadata filenames.
    private static final Set<String> INERT_METADATA_FILES = Set.of(
            ".gitignore", ".gitattributes", ".mailmap", ".editorconfig");

    /**
     * True when a changed path provably cannot affect any test outcome (Constitution §III),
     * so it selects no tests instead of triggering the conservative fallback.
     *
     * <p>This is an allowlist, never a blocklist — anything not matched stays {@code NON_SOURCE}.
     * Nothing under a {@code resources/} directory is ever inert: it may be test data a test
     * loads, which class-load tracking cannot observe, so it must fall back.
     */
    private static boolean isInert(String path) {
        if (path.startsWith("resources/") || path.contains("/resources/")) {
            return false;
        }
        if (path.startsWith(".github/") || path.startsWith(".idea/") || path.startsWith(".vscode/")
                || path.startsWith("docs/") || path.contains("/docs/")) {
            return true;
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (INERT_METADATA_FILES.contains(fileName) || fileName.endsWith(".iml")) {
            return true;
        }
        for (String stem : INERT_DOC_STEMS) {
            if (fileName.equals(stem) || fileName.startsWith(stem + ".")) {
                return true;
            }
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return INERT_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static String classNameFromPath(String path) {
        String marker = null;
        String extension;
        if (path.contains(MAIN_JAVA_MARKER) && path.endsWith(".java")) {
            marker = MAIN_JAVA_MARKER;
            extension = ".java";
        } else if (path.contains(TEST_JAVA_MARKER) && path.endsWith(".java")) {
            marker = TEST_JAVA_MARKER;
            extension = ".java";
        } else if (path.contains(MAIN_KOTLIN_MARKER) && path.endsWith(".kt")) {
            marker = MAIN_KOTLIN_MARKER;
            extension = ".kt";
        } else if (path.contains(TEST_KOTLIN_MARKER) && path.endsWith(".kt")) {
            marker = TEST_KOTLIN_MARKER;
            extension = ".kt";
        } else {
            return null;
        }
        String relative = path.substring(path.indexOf(marker) + marker.length());
        String withoutExtension = relative.substring(0, relative.length() - extension.length());
        return withoutExtension.replace('/', '.');
    }
}
