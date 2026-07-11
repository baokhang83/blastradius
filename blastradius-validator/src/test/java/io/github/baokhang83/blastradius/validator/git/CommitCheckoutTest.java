package io.github.baokhang83.blastradius.validator.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommitCheckoutTest {

    @Test
    void checkoutMaterializesTheRequestedCommitWithoutMutatingTheTargetRepo(
            @TempDir Path targetDir, @TempDir Path scratchParent) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(targetDir);
        fixture.writeClass("com.example.Foo", "package com.example; class Foo { int v = 1; }");
        String oldCommit = fixture.commit("v1");
        fixture.writeClass("com.example.Foo", "package com.example; class Foo { int v = 2; }");
        String newCommit = fixture.commit("v2");

        // Snapshot the target repo's state BEFORE any checkout activity.
        Path fooPath = targetDir.resolve("src/main/java/com/example/Foo.java");
        String contentBefore = Files.readString(fooPath, StandardCharsets.UTF_8);
        String headShaBefore;
        String branchBefore;
        try (Git git = Git.open(targetDir.toFile())) {
            Repository repo = git.getRepository();
            headShaBefore = repo.resolve("HEAD").getName();
            branchBefore = repo.getFullBranch();
        }
        assertEquals(newCommit, headShaBefore, "sanity check: fixture HEAD is the newest commit");

        // Use CommitCheckout to materialize the OLDER commit into a scratch location.
        try (CommitCheckout checkout = CommitCheckout.forTargetProject(targetDir, scratchParent)) {
            Path materialized = checkout.checkoutCommit(oldCommit);

            String materializedContent = Files.readString(
                    materialized.resolve("src/main/java/com/example/Foo.java"), StandardCharsets.UTF_8);
            assertTrue(materializedContent.contains("v = 1"),
                    "scratch working copy should reflect the OLD commit's content");

            // The scratch location must not be the target repo itself.
            assertTrue(!materialized.toAbsolutePath().equals(targetDir.toAbsolutePath()));
        }

        // The target repo's HEAD, branch, and working tree must be byte-identical to before.
        String contentAfter = Files.readString(fooPath, StandardCharsets.UTF_8);
        assertEquals(contentBefore, contentAfter, "target repo working tree must be unchanged");
        assertTrue(contentAfter.contains("v = 2"), "target repo must still reflect its own latest commit");

        try (Git git = Git.open(targetDir.toFile())) {
            Repository repo = git.getRepository();
            assertEquals(headShaBefore, repo.resolve("HEAD").getName(), "target repo HEAD must be unchanged");
            assertEquals(branchBefore, repo.getFullBranch(), "target repo branch must be unchanged");
        }
    }

    /**
     * Real bug found running the validator against jackson-databind: a commit whose
     * {@code pom.xml} fails to resolve (e.g. an unpublished SNAPSHOT parent) fails
     * during Maven's project-model-building phase, before the {@code clean} goal ever
     * runs — so a STALE {@code target/surefire-reports/TEST-*.xml} left over from the
     * *previous* commit's successful build survives into this checkout. {@link
     * io.github.baokhang83.blastradius.validator.build.BuildFailureDetector} only checks
     * whether any {@code TEST-*.xml} exists anywhere under the project — it can't tell a
     * fresh report from a stale one — so it wrongly concluded the (actually failed)
     * build must have run at least one test, and {@code RunCommand} went on to read
     * dependency-tracking output that was never produced, crashing the whole run instead
     * of gracefully excluding the one bad commit.
     */
    @Test
    void checkoutClearsStaleTargetDirectoryFromThePreviousCommit(
            @TempDir Path targetDir, @TempDir Path scratchParent) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(targetDir);
        String c1 = fixture.commit("initial");
        fixture.writeClass("com.example.Foo", "package com.example; class Foo {}");
        String c2 = fixture.commit("add Foo");

        try (CommitCheckout checkout = CommitCheckout.forTargetProject(targetDir, scratchParent)) {
            Path atC1 = checkout.checkoutCommit(c1);
            Path staleReport = atC1.resolve("target/surefire-reports/TEST-com.example.OldTest.xml");
            Files.createDirectories(staleReport.getParent());
            Files.writeString(staleReport, "<testsuite/>", StandardCharsets.UTF_8);
            assertTrue(Files.exists(staleReport), "sanity check: stale report was actually created");

            Path atC2 = checkout.checkoutCommit(c2);

            assertTrue(Files.notExists(atC2.resolve("target/surefire-reports/TEST-com.example.OldTest.xml")),
                    "a stale test report from the previous commit's build must not survive a checkout "
                            + "of a different commit");
        }
    }

    @Test
    void checkingOutMultipleCommitsSequentiallyReusesTheSameScratchClone(
            @TempDir Path targetDir, @TempDir Path scratchParent) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(targetDir);
        String c1 = fixture.commit("initial");
        fixture.writeClass("com.example.Foo", "package com.example; class Foo {}");
        String c2 = fixture.commit("add Foo");

        try (CommitCheckout checkout = CommitCheckout.forTargetProject(targetDir, scratchParent)) {
            Path atC1 = checkout.checkoutCommit(c1);
            assertTrue(Files.notExists(atC1.resolve("src/main/java/com/example/Foo.java")));

            Path atC2 = checkout.checkoutCommit(c2);
            assertTrue(Files.exists(atC2.resolve("src/main/java/com/example/Foo.java")));

            // Same scratch working directory reused across checkouts, not a new clone each time.
            assertEquals(atC1.toAbsolutePath(), atC2.toAbsolutePath());
        }
    }
}
