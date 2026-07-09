package io.github.baokhang83.blastradius.core.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import java.nio.file.Path;
import java.util.List;
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

    private static ChangedFile single(List<ChangedFile> files, String path) {
        return files.stream()
                .filter(f -> f.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no changed file with path " + path + " in " + files));
    }
}
