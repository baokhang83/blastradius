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

    /**
     * The gap {@link #checkoutClearsStaleTargetDirectoryFromThePreviousCommit} didn't cover:
     * in a multi-module reactor, a build can fail in one module before ever reaching another
     * that a prior commit successfully tested — leaving that other module's own
     * {@code target/} stale even though the reactor root's was cleared. Found running the
     * validator against apache/shenyu, a real multi-module project.
     */
    @Test
    void checkoutClearsStaleTargetDirectoriesInEverySubmoduleNotJustTheReactorRoot(
            @TempDir Path targetDir, @TempDir Path scratchParent) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.twoModuleReactor(targetDir);
        String c1 = fixture.commit("initial");
        fixture.writeClass("com.example.Bar", "package com.example; class Bar {}");
        String c2 = fixture.commit("add Bar");

        try (CommitCheckout checkout = CommitCheckout.forTargetProject(targetDir, scratchParent)) {
            Path atC1 = checkout.checkoutCommit(c1);
            Path staleReport =
                    atC1.resolve("moduleB/target/surefire-reports/TEST-com.example.OldTest.xml");
            Files.createDirectories(staleReport.getParent());
            Files.writeString(staleReport, "<testsuite/>", StandardCharsets.UTF_8);
            assertTrue(Files.exists(staleReport), "sanity check: stale report was actually created");

            Path atC2 = checkout.checkoutCommit(c2);

            assertTrue(
                    Files.notExists(
                            atC2.resolve("moduleB/target/surefire-reports/TEST-com.example.OldTest.xml")),
                    "a stale test report from the previous commit's build must not survive a checkout "
                            + "of a different commit, even when it lives in a submodule rather than "
                            + "the reactor root");
        }
    }

    @Test
    void isolatedMavenRepoIsASiblingOfTheWorkDirNotAChildSoCheckoutAndCleanupNeverWalkIt(
            @TempDir Path parent) {
        Path workDir = parent.resolve("blastradius-checkout-123");

        Path repo = CommitCheckout.isolatedMavenRepoFor(workDir);

        assertEquals(parent.toAbsolutePath().normalize(), repo.getParent(),
                "the isolated repo must be a sibling of the work dir, so git checkout and the "
                        + "target/-cleanup walk never descend into it");
        assertEquals("blastradius-checkout-123-m2", repo.getFileName().toString());
    }

    @Test
    void closeDeletesTheClonesIsolatedMavenRepoAlongsideItsScratchDir(
            @TempDir Path targetDir, @TempDir Path scratchParent) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(targetDir);
        fixture.commit("initial");

        Path scratchDir;
        Path isolatedRepo;
        try (CommitCheckout checkout = CommitCheckout.forTargetProject(targetDir, scratchParent)) {
            scratchDir = checkout.checkoutCommit("HEAD");
            isolatedRepo = CommitCheckout.isolatedMavenRepoFor(scratchDir);
            // Simulate a build having populated the clone's private local repo.
            Files.createDirectories(isolatedRepo.resolve("com/example"));
            Files.writeString(isolatedRepo.resolve("com/example/artifact.jar"), "cached", StandardCharsets.UTF_8);
            assertTrue(Files.exists(isolatedRepo), "sanity check: isolated repo was created");
        }

        // The scratch clone's own deletion is deliberately not asserted here: JGit can hold
        // memory-mapped locks on its .git pack files that keep them undeletable until GC on
        // Windows, so deleteRecursively is documented best-effort. The isolated Maven repo has
        // no such locks, so its cleanup — the behavior this test guards — is deterministic.
        assertTrue(Files.notExists(isolatedRepo),
                "close() must also delete the clone's isolated Maven repo, not leak it on disk");
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
