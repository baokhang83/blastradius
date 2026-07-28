package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Warning-only JDK-mismatch detection (no auto-selection): compares the target project's
 * declared {@code maven.compiler.release}/{@code target}/{@code source} against the JDK
 * that will actually run {@code mvn} (see {@link MavenBuildRunner}, which applies no
 * {@code JAVA_HOME} override and so inherits the validator's own JDK) — surfacing the
 * kind of mismatch found running against apache/shenyu (JDK 25 vs. a project's own JaCoCo
 * plugin unable to instrument JDK-25-generated class files).
 */
class JdkMismatchDetectorTest {

    private final JdkMismatchDetector detector = new JdkMismatchDetector();

    @Test
    void warnsWhenDeclaredReleaseDiffersFromTheRunningJdk(@TempDir Path projectRoot) throws IOException {
        writePom(projectRoot, "<maven.compiler.release>17</maven.compiler.release>");

        Optional<String> warning = detector.detect(projectRoot, 25);

        assertTrue(warning.isPresent());
        assertTrue(warning.get().contains("17"), warning.get());
        assertTrue(warning.get().contains("25"), warning.get());
    }

    @Test
    void noWarningWhenDeclaredMatchesTheRunningJdk(@TempDir Path projectRoot) throws IOException {
        writePom(projectRoot, "<maven.compiler.release>21</maven.compiler.release>");

        assertEquals(Optional.empty(), detector.detect(projectRoot, 21));
    }

    @Test
    void noWarningWhenNoJdkVersionIsDeclared(@TempDir Path projectRoot) throws IOException {
        writePom(projectRoot, "<some.other.property>x</some.other.property>");

        assertEquals(Optional.empty(), detector.detect(projectRoot, 21));
    }

    @Test
    void noWarningWhenThereIsNoPomAtAll(@TempDir Path projectRoot) {
        assertEquals(Optional.empty(), detector.detect(projectRoot, 21));
    }

    @Test
    void fallsBackToSourceWhenReleaseAndTargetAreAbsent(@TempDir Path projectRoot) throws IOException {
        writePom(projectRoot, "<maven.compiler.source>1.8</maven.compiler.source>");

        Optional<String> warning = detector.detect(projectRoot, 21);

        assertTrue(warning.isPresent());
        assertTrue(warning.get().contains("8"), warning.get());
    }

    @Test
    void releaseTakesPrecedenceOverTarget(@TempDir Path projectRoot) throws IOException {
        writePom(projectRoot, """
                <maven.compiler.release>17</maven.compiler.release>
                <maven.compiler.target>11</maven.compiler.target>
                """);

        Optional<String> warning = detector.detect(projectRoot, 17);

        assertEquals(Optional.empty(), warning);
    }

    @Test
    void ignoresAnUnresolvablePropertyExpression(@TempDir Path projectRoot) throws IOException {
        writePom(projectRoot, "<maven.compiler.release>${java.version}</maven.compiler.release>");

        assertEquals(Optional.empty(), detector.detect(projectRoot, 21));
    }

    @Test
    void fallsBackToTheJavaVersionPropertyWhenNoStandardCompilerPropertyIsDeclared(@TempDir Path projectRoot)
            throws IOException {
        // apache/shenyu's own convention: no maven.compiler.release/target/source at all,
        // just a custom java.version property read by its own plugin configs.
        writePom(projectRoot, "<java.version>17</java.version>");

        Optional<String> warning = detector.detect(projectRoot, 25);

        assertTrue(warning.isPresent());
        assertTrue(warning.get().contains("17"), warning.get());
        assertTrue(warning.get().contains("25"), warning.get());
    }

    @Test
    void standardCompilerPropertyTakesPrecedenceOverJavaVersion(@TempDir Path projectRoot) throws IOException {
        writePom(projectRoot, """
                <maven.compiler.release>17</maven.compiler.release>
                <java.version>11</java.version>
                """);

        Optional<String> warning = detector.detect(projectRoot, 17);

        assertEquals(Optional.empty(), warning);
    }

    private static void writePom(Path projectRoot, String properties) throws IOException {
        Files.writeString(projectRoot.resolve("pom.xml"), """
                <project>
                  <properties>
                %s
                  </properties>
                </project>
                """.formatted(properties.indent(4)));
    }
}
