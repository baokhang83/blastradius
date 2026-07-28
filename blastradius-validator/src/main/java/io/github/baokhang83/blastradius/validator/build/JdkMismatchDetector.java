package io.github.baokhang83.blastradius.validator.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Warns, upfront, when the target project's own declared JDK requirement differs from the
 * JDK that will actually run {@code mvn} — never selects or switches JDKs, only reports
 * the mismatch. {@link MavenBuildRunner} applies no {@code JAVA_HOME} override, so the
 * subprocess inherits whichever JDK is running the validator itself; a real-project run
 * against apache/shenyu found that a mismatch here (JDK 25 vs. a project's own JaCoCo
 * plugin unable to instrument JDK-25-generated class files) can crash the tracking
 * agent's shutdown hook well before any error surfaces as an obvious build failure.
 *
 * <p>Best-effort only: reads the target's root {@code pom.xml} directly, not the fully
 * resolved effective POM, and a declared value that isn't a plain integer (e.g. an
 * unresolved {@code ${property}} expression) is silently skipped rather than warned about.
 */
public final class JdkMismatchDetector {

    private static final List<String> JDK_VERSION_PROPERTIES =
            List.of("maven.compiler.release", "maven.compiler.target", "maven.compiler.source");

    /** Compares against the JDK actually running this validator process. */
    public Optional<String> detect(Path projectRoot) {
        return detect(projectRoot, Runtime.version().feature());
    }

    Optional<String> detect(Path projectRoot, int runningJdkFeatureVersion) {
        return declaredJdkVersion(projectRoot)
                .filter(declared -> declared != runningJdkFeatureVersion)
                .map(declared -> "warning: " + projectRoot + " declares JDK " + declared
                        + " (via maven.compiler.release/target/source) but this run will build it "
                        + "with JDK " + runningJdkFeatureVersion + " — a mismatch here can make the "
                        + "target project's own build/coverage tooling fail in ways unrelated to the "
                        + "code under test.");
    }

    private static Optional<Integer> declaredJdkVersion(Path projectRoot) {
        Path pomFile = projectRoot.resolve("pom.xml");
        if (!Files.isRegularFile(pomFile)) {
            return Optional.empty();
        }
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            doc = factory.newDocumentBuilder().parse(pomFile.toFile());
        } catch (Exception e) {
            return Optional.empty();
        }
        for (String propertyName : JDK_VERSION_PROPERTIES) {
            NodeList nodes = doc.getElementsByTagName(propertyName);
            if (nodes.getLength() == 0) {
                continue;
            }
            Integer parsed = parseFeatureVersion(nodes.item(0).getTextContent().trim());
            if (parsed != null) {
                return Optional.of(parsed);
            }
        }
        return Optional.empty();
    }

    private static Integer parseFeatureVersion(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        String normalized = raw.startsWith("1.") ? raw.substring(2) : raw;
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
