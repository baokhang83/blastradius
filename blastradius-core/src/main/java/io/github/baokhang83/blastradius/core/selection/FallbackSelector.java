package io.github.baokhang83.blastradius.core.selection;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import io.github.baokhang83.blastradius.core.git.FileKind;
import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.List;

/**
 * The conservative fallback rule (FR-006, Constitution Principle III): any non-Java-source
 * change (resources, build/dependency configuration, migrations) selects every test.
 */
public final class FallbackSelector {

    public boolean shouldFallback(List<ChangedFile> changedFiles) {
        return changedFiles.stream().anyMatch(f -> f.kind() == FileKind.NON_SOURCE);
    }

    public SelectionDecision select(TestIdentity test) {
        return SelectionDecision.fallback(test);
    }
}
