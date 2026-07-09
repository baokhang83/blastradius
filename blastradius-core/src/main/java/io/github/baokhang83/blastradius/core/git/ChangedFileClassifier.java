package io.github.baokhang83.blastradius.core.git;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 * Diffs a commit pair and classifies each changed file as {@link FileKind#JAVA_SOURCE}
 * (subject to dependency-match selection) or {@link FileKind#NON_SOURCE} (subject to the
 * conservative fallback rule, per FR-006). Java source class names are derived from the
 * standard Maven {@code src/main/java/} / {@code src/test/java/} layout, which also
 * makes multi-module reactors module-agnostic here (research.md #5).
 */
public final class ChangedFileClassifier {

    private static final String MAIN_JAVA_MARKER = "src/main/java/";
    private static final String TEST_JAVA_MARKER = "src/test/java/";

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
                    result.add(classifyPath(path));
                }
                return result;
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to classify changes between " + baseCommit + " and " + headCommit, e);
        }
    }

    private static ChangedFile classifyPath(String path) {
        String className = classNameFromPath(path);
        if (className != null) {
            return new ChangedFile(path, FileKind.JAVA_SOURCE, className);
        }
        return new ChangedFile(path, FileKind.NON_SOURCE, null);
    }

    private static String classNameFromPath(String path) {
        String marker = null;
        if (path.contains(MAIN_JAVA_MARKER)) {
            marker = MAIN_JAVA_MARKER;
        } else if (path.contains(TEST_JAVA_MARKER)) {
            marker = TEST_JAVA_MARKER;
        }
        if (marker == null || !path.endsWith(".java")) {
            return null;
        }
        String relative = path.substring(path.indexOf(marker) + marker.length());
        String withoutExtension = relative.substring(0, relative.length() - ".java".length());
        return withoutExtension.replace('/', '.');
    }
}
