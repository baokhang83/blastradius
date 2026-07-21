package io.github.baokhang83.blastradius.plugin.diff;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.ChangedFileClassifier;
import io.github.baokhang83.blastradius.core.git.GitComparison;
import io.github.baokhang83.blastradius.core.git.MergeBaseResolver;
import java.nio.file.Path;
import java.util.List;

/**
 * Resolves the current build's changes relative to a configured base reference
 * (data-model.md's {@link CurrentChanges}), reusing {@code blastradius-core}'s proven
 * {@link ChangedFileClassifier} for the actual diff — the same mechanism the shadow-mode
 * validator already relies on, applied live instead of to historical commit pairs.
 */
public final class CurrentChangesResolver {

    private final ChangedFileClassifier changedFileClassifier = new ChangedFileClassifier();
    private final MergeBaseResolver mergeBaseResolver = new MergeBaseResolver();

    public CurrentChanges resolve(Path projectDir, String baseReference) {
        GitComparison comparison = mergeBaseResolver.resolve(projectDir, baseReference);
        List<ChangedFile> changedFiles = comparison.baseReferenceBuild()
                ? List.of()
                : comparison.comparisonBaseCommit()
                        .map(comparisonBase -> changedFileClassifier.classify(
                                projectDir, comparisonBase, comparison.headCommit()))
                        .orElseGet(List::of);

        return new CurrentChanges(
                baseReference,
                comparison.baseReferenceCommit(),
                comparison.comparisonBaseCommit(),
                comparison.headCommit(),
                comparison.baseReferenceBuild(),
                changedFiles);
    }
}
