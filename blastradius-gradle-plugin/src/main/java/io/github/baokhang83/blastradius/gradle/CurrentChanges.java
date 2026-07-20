package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.git.ChangedFile;
import java.util.List;

record CurrentChanges(String currentCommit, boolean baseRefBuild, List<ChangedFile> changedFiles) {}
