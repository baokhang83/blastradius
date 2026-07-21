package io.github.baokhang83.blastradius.core.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MergeBaseResolverTest {

    private final MergeBaseResolver resolver = new MergeBaseResolver();

    @Test
    void resolvesTheBranchPointWhenTheTargetBranchAdvances(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        String branchPoint = fixture.commit("initial");

        try (Git git = Git.open(projectDir.toFile())) {
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
        }
        fixture.writeClass("com.example.FeatureOnly", "package com.example; class FeatureOnly {}");
        String featureHead = fixture.commit("feature change");

        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setName("main").call();
        }
        fixture.writeClass("com.example.TargetOnly", "package com.example; class TargetOnly {}");
        String targetTip = fixture.commit("target change");

        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setName("feature").call();
        }

        GitComparison comparison = resolver.resolve(projectDir, "main");

        assertEquals(featureHead, comparison.headCommit());
        assertEquals(targetTip, comparison.baseReferenceCommit());
        assertEquals(branchPoint, comparison.comparisonBaseCommit().orElseThrow());
        assertFalse(comparison.baseReferenceBuild());
    }

    @Test
    void recognizesTheBaseReferenceBuild(@TempDir Path projectDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        String mainHead = fixture.commit("initial");

        GitComparison comparison = resolver.resolve(projectDir, "main");

        assertEquals(mainHead, comparison.comparisonBaseCommit().orElseThrow());
        assertTrue(comparison.baseReferenceBuild());
    }

    @Test
    void reportsNoComparisonBaseForUnrelatedHistories(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.commit("main history");

        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setName("unrelated").setOrphan(true).call();
        }
        fixture.writeResource("unrelated.txt", "unrelated history");
        fixture.commit("unrelated history");

        GitComparison comparison = resolver.resolve(projectDir, "main");

        assertTrue(comparison.comparisonBaseCommit().isEmpty());
        assertFalse(comparison.baseReferenceBuild());
    }

    @Test
    void resolvesTheTargetTipAfterTheFeatureMergesIt(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.commit("initial");

        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setCreateBranch(true).setName("feature").call();
        }
        fixture.writeClass("com.example.FeatureOnly", "package com.example; class FeatureOnly {}");
        fixture.commit("feature change");

        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setName("main").call();
        }
        fixture.writeClass("com.example.TargetOnly", "package com.example; class TargetOnly {}");
        String targetTip = fixture.commit("target change");

        String mergeHead;
        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setName("feature").call();
            git.merge().include(git.getRepository().resolve("main")).call();
            mergeHead = git.getRepository().resolve("HEAD").getName();
        }

        GitComparison comparison = resolver.resolve(projectDir, "main");

        assertEquals(mergeHead, comparison.headCommit());
        assertEquals(targetTip, comparison.comparisonBaseCommit().orElseThrow());
    }

    @Test
    void resolvesTheTargetTipAfterAFeatureIsRebasedOntoIt(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(projectDir);
        fixture.commit("initial");

        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setCreateBranch(true).setName("feature").call();
        }
        fixture.writeClass("com.example.FeatureOnly", "package com.example; class FeatureOnly {}");
        fixture.commit("feature change");

        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setName("main").call();
        }
        fixture.writeClass("com.example.TargetOnly", "package com.example; class TargetOnly {}");
        String targetTip = fixture.commit("target change");

        try (Git git = Git.open(projectDir.toFile())) {
            git.checkout().setName("feature").call();
            git.rebase().setUpstream("main").call();
        }

        GitComparison comparison = resolver.resolve(projectDir, "main");

        assertEquals(targetTip, comparison.comparisonBaseCommit().orElseThrow());
    }
}
