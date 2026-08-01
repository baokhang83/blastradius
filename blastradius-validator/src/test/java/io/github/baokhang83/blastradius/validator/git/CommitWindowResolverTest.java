package io.github.baokhang83.blastradius.validator.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Path;
import java.util.Set;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommitWindowResolverTest {

    private final CommitWindowResolver resolver = new CommitWindowResolver();

    @Test
    void resolvesRequestedWindowSizeAsOrderedConsecutivePairs(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String c1 = fixture.commit("initial");
        fixture.writeClass("com.example.Foo", "package com.example; class Foo {}");
        String c2 = fixture.commit("add Foo");
        fixture.writeClass("com.example.Bar", "package com.example; class Bar {}");
        String c3 = fixture.commit("add Bar");
        fixture.writeClass("com.example.Baz", "package com.example; class Baz {}");
        String c4 = fixture.commit("add Baz");

        List<CommitPair> window = resolver.resolveWindow(tempDir, 3);

        assertEquals(3, window.size());
        // oldest pair first, chronological order
        assertEquals(new PairKey(c1, c2), keyOf(window.get(0)));
        assertEquals(new PairKey(c2, c3), keyOf(window.get(1)));
        assertEquals(new PairKey(c3, c4), keyOf(window.get(2)));
        window.forEach(p -> assertEquals(PairStatus.ANALYZED, p.status()));
    }

    @Test
    void windowLargerThanHistoryReturnsWhatExists(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String c1 = fixture.commit("initial");
        String c2 = fixture.commit("second");

        List<CommitPair> window = resolver.resolveWindow(tempDir, 10);

        assertEquals(1, window.size());
        assertEquals(new PairKey(c1, c2), keyOf(window.get(0)));
    }

    @Test
    void singleCommitHistoryYieldsNoPairs(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        fixture.commit("only commit");

        List<CommitPair> window = resolver.resolveWindow(tempDir, 5);

        assertTrue(window.isEmpty());
    }

    @Test
    void defaultModeReplaysEveryDirectParentEdgeOfAReachableMerge(@TempDir Path tempDir) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        fixture.commit("initial");
        Git git = fixture.git();

        git.checkout().setCreateBranch(true).setName("feature").call();
        fixture.writeResource("feature.txt", "feature");
        String featureParent = fixture.commit("feature work");

        git.checkout().setName("main").call();
        fixture.writeResource("main.txt", "main");
        String firstParent = fixture.commit("main work");

        git.merge().include(git.getRepository().resolve(featureParent)).call();
        String merge = git.getRepository().resolve("HEAD").getName();

        List<CommitPair> window = resolver.resolveWindow(tempDir, 2);

        assertEquals(Set.of(new PairKey(firstParent, merge), new PairKey(featureParent, merge)),
                window.stream().map(CommitWindowResolverTest::keyOf).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void firstParentModeReplaysOnlyTheMainlineEdgeOfAReachableMerge(@TempDir Path tempDir) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        fixture.commit("initial");
        Git git = fixture.git();

        git.checkout().setCreateBranch(true).setName("feature").call();
        fixture.writeResource("feature.txt", "feature");
        String featureParent = fixture.commit("feature work");

        git.checkout().setName("main").call();
        fixture.writeResource("main.txt", "main");
        String firstParent = fixture.commit("main work");

        git.merge().include(git.getRepository().resolve(featureParent)).call();
        String merge = git.getRepository().resolve("HEAD").getName();

        List<CommitPair> window = resolver.resolveWindow(tempDir, 1, HistoryMode.FIRST_PARENT);

        assertEquals(List.of(new PairKey(firstParent, merge)), window.stream().map(CommitWindowResolverTest::keyOf).toList());
    }

    private record PairKey(String base, String head) {}

    private static PairKey keyOf(CommitPair pair) {
        return new PairKey(pair.baseCommit(), pair.headCommit());
    }
}
