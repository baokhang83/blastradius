package io.github.baokhang83.blastradius.core.index;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Derives a stable, root-relative index key for one full Git commit ID. */
public final class CommitIndexKey {

    private static final Pattern FULL_COMMIT_ID = Pattern.compile("[0-9a-fA-F]{40}");

    private CommitIndexKey() {}

    /**
     * Inserts {@code commit} immediately before the configured index file name.
     *
     * @param indexPathKey root-relative configured index-file key
     * @param commit full resolved Git commit ID
     * @return a slash-separated, root-relative key for that commit
     */
    public static String forCommit(String indexPathKey, String commit) {
        if (commit == null || !FULL_COMMIT_ID.matcher(commit).matches()) {
            throw new IllegalArgumentException("commit must be a full Git object ID: " + commit);
        }
        Path indexPath = relativeIndexPath(indexPathKey);
        List<String> parts = new ArrayList<>();
        for (Path part : indexPath.getParent() == null ? List.<Path>of() : indexPath.getParent()) {
            parts.add(part.toString());
        }
        parts.add(commit.toLowerCase(Locale.ROOT));
        parts.add(indexPath.getFileName().toString());
        return String.join("/", parts);
    }

    private static Path relativeIndexPath(String indexPathKey) {
        if (indexPathKey == null || indexPathKey.isBlank()) {
            throw new IllegalArgumentException("index path key must not be blank");
        }
        try {
            Path normalized = Path.of(indexPathKey).normalize();
            if (normalized.isAbsolute() || normalized.getNameCount() == 0 || normalized.startsWith("..")) {
                throw new IllegalArgumentException("index path key must stay below its configured root: " + indexPathKey);
            }
            return normalized;
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("invalid index path key: " + indexPathKey, e);
        }
    }
}
