package io.github.baokhang83.blastradius.core.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangedFileClassifierTest {

    private final ChangedFileClassifier classifier = new ChangedFileClassifier();

    @Test
    void mainJavaSourceChangeIsClassifiedWithDerivedClassName(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String base = fixture.commit("initial");
        fixture.writeClass("com.example.Foo", "package com.example; class Foo {}");
        String head = fixture.commit("add Foo");

        List<ChangedFile> changed = classifier.classify(tempDir, base, head);

        ChangedFile foo = single(changed, "src/main/java/com/example/Foo.java");
        assertEquals(FileKind.JAVA_SOURCE, foo.kind());
        assertEquals("com.example.Foo", foo.changedClassName());
    }

    @Test
    void testJavaSourceChangeIsClassifiedWithDerivedClassName(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String base = fixture.commit("initial");
        fixture.writeTest("com.example.FooTest", "package com.example; class FooTest {}");
        String head = fixture.commit("add FooTest");

        List<ChangedFile> changed = classifier.classify(tempDir, base, head);

        ChangedFile fooTest = single(changed, "src/test/java/com/example/FooTest.java");
        assertEquals(FileKind.JAVA_SOURCE, fooTest.kind());
        assertEquals("com.example.FooTest", fooTest.changedClassName());
    }

    @Test
    void kotlinSourceChangeProvidesClassAndFileFacadeCandidates(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String base = fixture.commit("initial");
        fixture.writeResource("src/main/kotlin/com/example/Greeting.kt", """
                package com.example
                class Greeting
                """);
        String head = fixture.commit("add Kotlin greeting");

        ChangedFile greeting = single(classifier.classify(tempDir, base, head),
                "src/main/kotlin/com/example/Greeting.kt");

        assertEquals(FileKind.JAVA_SOURCE, greeting.kind());
        assertEquals("com.example.Greeting", greeting.changedClassName());
        assertEquals(Set.of("com.example.Greeting", "com.example.GreetingKt"),
                greeting.candidateClassNames());
    }

    @Test
    void kotlinInlineFunctionChangeFallsBackToTheFullSuite(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        fixture.writeResource("src/main/kotlin/com/example/Greeting.kt", """
                package com.example
                fun greeting() = "hello"
                """);
        String base = fixture.commit("ordinary Kotlin greeting");
        fixture.writeResource("src/main/kotlin/com/example/Greeting.kt", """
                package com.example
                inline suspend fun greeting() = "hello"
                """);
        String head = fixture.commit("inline Kotlin greeting");

        ChangedFile greeting = single(classifier.classify(tempDir, base, head),
                "src/main/kotlin/com/example/Greeting.kt");

        assertEquals(FileKind.NON_SOURCE, greeting.kind());
        assertNull(greeting.changedClassName());
    }

    @Test
    void pomChangeIsClassifiedAsNonSource(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String base = fixture.commit("initial");
        fixture.writeResource("pom.xml", "<project><!-- modified --></project>");
        String head = fixture.commit("touch pom");

        List<ChangedFile> changed = classifier.classify(tempDir, base, head);

        ChangedFile pom = single(changed, "pom.xml");
        assertEquals(FileKind.NON_SOURCE, pom.kind());
        assertNull(pom.changedClassName());
    }

    @Test
    void resourceFileChangeIsClassifiedAsNonSource(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String base = fixture.commit("initial");
        fixture.writeResource("src/main/resources/app.properties", "key=value");
        String head = fixture.commit("add resource");

        List<ChangedFile> changed = classifier.classify(tempDir, base, head);

        ChangedFile resource = single(changed, "src/main/resources/app.properties");
        assertEquals(FileKind.NON_SOURCE, resource.kind());
    }

    @Test
    void multipleChangedFilesInOneCommitAreAllClassified(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String base = fixture.commit("initial");
        fixture.writeClass("com.example.Foo", "package com.example; class Foo {}");
        fixture.writeResource("src/main/resources/app.properties", "key=value");
        String head = fixture.commit("add Foo and resource");

        List<ChangedFile> changed = classifier.classify(tempDir, base, head);

        assertEquals(2, changed.size());
    }

    @Test
    void readmeChangeIsClassifiedAsInert(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String base = fixture.commit("initial");
        fixture.writeResource("README.md", "# docs edit");
        String head = fixture.commit("edit readme");

        List<ChangedFile> changed = classifier.classify(tempDir, base, head);

        ChangedFile readme = single(changed, "README.md");
        assertEquals(FileKind.INERT, readme.kind());
        assertNull(readme.changedClassName());
    }

    @Test
    void ciWorkflowChangeIsClassifiedAsInert(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String base = fixture.commit("initial");
        fixture.writeResource(".github/workflows/ci.yml", "name: ci");
        String head = fixture.commit("edit ci");

        List<ChangedFile> changed = classifier.classify(tempDir, base, head);

        assertEquals(FileKind.INERT, single(changed, ".github/workflows/ci.yml").kind());
    }

    @Test
    void docImageChangeIsClassifiedAsInert(@TempDir Path tempDir) {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String base = fixture.commit("initial");
        fixture.writeResource("docs/logo.png", "fake-png-bytes");
        String head = fixture.commit("add logo");

        List<ChangedFile> changed = classifier.classify(tempDir, base, head);

        assertEquals(FileKind.INERT, single(changed, "docs/logo.png").kind());
    }

    @Test
    void markdownUnderResourcesIsNonSourceNotInert(@TempDir Path tempDir) {
        // Soundness (§III): a Markdown file under resources/ can be test data a test loads,
        // and class-load tracking can't see it — so it MUST fall back, never select nothing.
        FixtureProjectBuilder fixture = FixtureProjectBuilder.singleModule(tempDir);
        String base = fixture.commit("initial");
        fixture.writeResource("src/test/resources/fixture.md", "expected: 42");
        String head = fixture.commit("edit fixture");

        List<ChangedFile> changed = classifier.classify(tempDir, base, head);

        assertEquals(FileKind.NON_SOURCE, single(changed, "src/test/resources/fixture.md").kind());
    }

    private static ChangedFile single(List<ChangedFile> files, String path) {
        return files.stream()
                .filter(f -> f.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no changed file with path " + path + " in " + files));
    }
}
