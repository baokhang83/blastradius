package io.github.baokhang83.blastradius.validator.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyntheticMutationCheckoutTest {

    @Test
    void commitsTheMutationAsADirectChildWithoutChangingTheTargetWorktree(
            @TempDir Path project, @TempDir Path scratchParent) throws Exception {
        String source = "package com.example; class Flag { boolean value() { return true; } }";
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(project);
        fixture.writeClass("com.example.Flag", source);
        fixture.commit("baseline");
        String baseline = head(project);
        Path targetSource = project.resolve("src/main/java/com/example/Flag.java");
        MutationCandidate candidate = new MutationCandidate(
                "src/main/java/com/example/Flag.java", "com.example.Flag", MutationOperator.BOOLEAN_LITERAL,
                source.indexOf("true"), "true", "false");

        String mutant;
        try (SyntheticMutationCheckout checkout = SyntheticMutationCheckout.forTargetProject(project, scratchParent)) {
            checkout.checkoutBaseline(baseline);
            mutant = checkout.commit(candidate);

            try (Git scratch = Git.open(checkout.workTree().toFile())) {
                assertEquals(baseline, scratch.getRepository().parseCommit(ObjectId.fromString(mutant))
                        .getParent(0).getName());
            }
            assertFalse(Files.readString(checkout.workTree().resolve(candidate.sourcePath())).contains("return true"));
        }

        assertEquals(baseline, head(project));
        assertEquals(source, Files.readString(targetSource));
        try (Git target = Git.open(project.toFile())) {
            assertTrueClean(target);
        }
    }

    private String head(Path project) throws Exception {
        try (Git git = Git.open(project.toFile())) {
            return git.getRepository().resolve("HEAD").name();
        }
    }

    private void assertTrueClean(Git git) throws Exception {
        assertFalse(git.status().call().hasUncommittedChanges());
    }
}
