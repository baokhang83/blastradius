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

    private String describe(MutationCandidate candidate) {
        return candidate.className() + ":" + candidate.operator() + ":" + candidate.original()
                + ":" + candidate.replacement();
    }
}
