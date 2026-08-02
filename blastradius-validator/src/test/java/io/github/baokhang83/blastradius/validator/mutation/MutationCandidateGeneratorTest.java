package io.github.baokhang83.blastradius.validator.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MutationCandidateGeneratorTest {

    private final MutationCandidateGenerator generator = new MutationCandidateGenerator();

    @Test
    void generatesSortedProductionCandidatesAndIgnoresCommentsAndStrings(@TempDir Path project) throws Exception {
        write(project, "com.example.Beta", """
                package com.example;
                class Beta {
                    boolean value() { return false; }
                }
                """);
        write(project, "com.example.Alpha", """
                package com.example;
                class Alpha {
                    boolean value(int left, int right) {
                        // true == false
                        String ignored = "true != false";
                        return true && left == right;
                    }
                }
                """);

        List<MutationCandidate> candidates = generator.generate(project, null, 10);

        assertEquals(List.of(
                "com.example.Alpha:BOOLEAN_LITERAL:true:false",
                "com.example.Alpha:EQUALITY_OPERATOR:==:!=",
                "com.example.Beta:BOOLEAN_LITERAL:false:true"),
                candidates.stream().map(this::describe).toList());
    }

    @Test
    void filtersByExactClassAndAppliesTheLimitAfterStableOrdering(@TempDir Path project) throws Exception {
        write(project, "com.example.Beta", """
                package com.example;
                class Beta { boolean value() { return false; } }
                """);
        write(project, "com.example.Alpha", """
                package com.example;
                class Alpha { boolean value() { return true; } }
                """);

        List<MutationCandidate> filtered = generator.generate(project, "com.example.Beta", 10);
        List<MutationCandidate> limited = generator.generate(project, null, 1);

        assertEquals(List.of("com.example.Beta:BOOLEAN_LITERAL:false:true"),
                filtered.stream().map(this::describe).toList());
        assertEquals(List.of("com.example.Alpha:BOOLEAN_LITERAL:true:false"),
                limited.stream().map(this::describe).toList());
    }

    @Test
    void classBudgetCountsClassesThatYieldCandidatesNotFilesLookedAt(@TempDir Path project) throws Exception {
        // The alphabetically-first source has NO mutable token (an abstract class with no
        // boolean/operator literal). With a 1-class budget it must NOT consume that budget and
        // return empty — the budget bounds classes we actually mutate. This is the apache/shenyu
        // failure: --max-mutation-classes-per-pair 1 landed on a token-free first class and
        // generated zero mutants while thousands of mutable classes went unvisited.
        write(project, "com.example.AbstractFirst", """
                package com.example;
                abstract class AbstractFirst { abstract void run(); }
                """);
        write(project, "com.example.Zeta", """
                package com.example;
                class Zeta { boolean value() { return true; } }
                """);

        List<MutationCandidate> candidates = generator.generate(project, null, 1, 10);

        assertEquals(List.of("com.example.Zeta:BOOLEAN_LITERAL:true:false"),
                candidates.stream().map(this::describe).toList());
    }

    @Test
    void discoversPerModuleSourceRootsInAMultiModuleReactorWithNoRootSources(@TempDir Path project)
            throws Exception {
        // A real reactor (apache/shenyu) has NO src/main/java at the repo root — every source
        // lives in a submodule. The generator must find those per-module roots, and each
        // candidate's sourcePath must be REPO-relative (module dir included) so the mutation
        // validator can resolve it to its owning module for -pl scoping.
        writeInModule(project, "shenyu-common", "org.apache.shenyu.common.Flag", """
                package org.apache.shenyu.common;
                public class Flag { public boolean on() { return true; } }
                """);
        writeInModule(project, "shenyu-admin", "org.apache.shenyu.admin.Gate", """
                package org.apache.shenyu.admin;
                public class Gate { public boolean open() { return false; } }
                """);

        List<MutationCandidate> candidates = generator.generate(project, null, 10);

        assertEquals(List.of(
                "shenyu-admin/src/main/java/org/apache/shenyu/admin/Gate.java"
                        + "|org.apache.shenyu.admin.Gate|BOOLEAN_LITERAL",
                "shenyu-common/src/main/java/org/apache/shenyu/common/Flag.java"
                        + "|org.apache.shenyu.common.Flag|BOOLEAN_LITERAL"),
                candidates.stream().map(this::describePath).toList());
    }

    @Test
    void ignoresTestSourcesAndBuildOutputWhenDiscoveringModuleRoots(@TempDir Path project) throws Exception {
        writeInModule(project, "mod", "com.example.Prod", """
                package com.example;
                public class Prod { public boolean on() { return true; } }
                """);
        // A test source under src/test/java must never be mutated (only production code is).
        Path testFile = project.resolve("mod/src/test/java/com/example/ProdTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "package com.example; class ProdTest { boolean b = false; }");
        // Stale build output that happens to sit under a target/ dir must be pruned, not scanned.
        Path built = project.resolve("mod/target/generated-sources/com/example/Junk.java");
        Files.createDirectories(built.getParent());
        Files.writeString(built, "package com.example; class Junk { boolean b = true; }");

        List<MutationCandidate> candidates = generator.generate(project, null, 10);

        assertEquals(List.of("com.example.Prod"),
                candidates.stream().map(MutationCandidate::className).distinct().toList());
    }

    @Test
    void candidateAppliesItsSingleReplacementAtTheRecordedOffset(@TempDir Path project) throws Exception {
        Path source = write(project, "com.example.Alpha", """
                package com.example;
                class Alpha { boolean value() { return true; } }
                """);
        MutationCandidate candidate = generator.generate(project, null, 1).getFirst();

        String mutated = candidate.applyTo(Files.readString(source));

        assertFalse(mutated.contains("return true"));
        assertEquals("false", candidate.replacement());
    }

    private Path write(Path project, String className, String source) throws Exception {
        Path file = project.resolve("src/main/java/" + className.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }

    private Path writeInModule(Path project, String module, String className, String source) throws Exception {
        Path file = project.resolve(module + "/src/main/java/" + className.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }

    private String describe(MutationCandidate candidate) {
        return candidate.className() + ":" + candidate.operator() + ":" + candidate.original()
                + ":" + candidate.replacement();
    }

    private String describePath(MutationCandidate candidate) {
        return candidate.sourcePath() + "|" + candidate.className() + "|" + candidate.operator();
    }
}
