package io.github.baokhang83.blastradius.gradle;

import org.gradle.api.Action;
import org.gradle.api.Task;

/** Leaves tests unfiltered when Git cannot establish a common comparison base. */
final class NoComparisonBaseFallbackAction implements Action<Task> {

    @Override
    public void execute(Task task) {
        task.getLogger().lifecycle(
                "[blastradius] FALLBACK — Git could not establish a common comparison base (MERGE_BASE_UNAVAILABLE)");
    }
}
