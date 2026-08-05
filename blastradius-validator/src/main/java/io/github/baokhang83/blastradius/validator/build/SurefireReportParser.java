package io.github.baokhang83.blastradius.validator.build;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Parses Maven Surefire/Failsafe {@code TEST-*.xml} report files into per-test pass/fail
 * results (research.md #3) — the authoritative, standard output of the build that was
 * just run, rather than a second, potentially-disagreeing mechanism.
 */
public final class SurefireReportParser {

    /**
     * Characters XML 1.0 forbids outright (C0 controls other than tab/LF/CR) plus the
     * {@code U+FFFE}/{@code U+FFFD}-adjacent noncharacters — observed verbatim in a Surefire
     * failure message copied from raw bytes of the process under test (e.g. a corrupted response
     * body surfacing in an assertion message). A strict parser rejects the whole file over one
     * such byte, turning one unrepresentable character into a lost pair; stripping them here
     * keeps the rest of the file's evidence instead of discarding it.
     */
    private static final Pattern INVALID_XML_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]");

    /**
     * @param reportsDir a Surefire or Failsafe reports directory (e.g.
     *                   {@code target/surefire-reports}); missing/absent yields an empty
     *                   result rather than an error, since a module may have no tests
     * @return per-test outcome; {@code true} = passed, {@code false} = failed. Skipped
     *         test cases carry no ground-truth signal and are omitted entirely.
     */
    public Map<TestIdentity, Boolean> parse(Path reportsDir) {
        if (Files.notExists(reportsDir)) {
            return Map.of();
        }
        Map<TestIdentity, Boolean> results = new HashMap<>();
        try (Stream<Path> files = Files.list(reportsDir)) {
            List<Path> reportFiles = files
                    .filter(p -> p.getFileName().toString().startsWith("TEST-"))
                    .filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .toList();
            for (Path reportFile : reportFiles) {
                parseOneFile(reportFile, results);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("failed to list reports in " + reportsDir, e);
        }
        return results;
    }

    private static void parseOneFile(Path reportFile, Map<TestIdentity, Boolean> results) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(sanitizedXml(reportFile))));

            NodeList testcases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < testcases.getLength(); i++) {
                Element testcase = (Element) testcases.item(i);
                String className = testcase.getAttribute("classname");
                String methodName = testcase.getAttribute("name");
                TestIdentity identity = new TestIdentity(className, methodName);

                if (hasChildElement(testcase, "skipped")) {
                    continue;
                }
                boolean passed = !hasChildElement(testcase, "failure") && !hasChildElement(testcase, "error");
                results.put(identity, passed);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse report file " + reportFile, e);
        }
    }

    /**
     * Reads {@code reportFile} as UTF-8 (Surefire's default report encoding), tolerating
     * malformed/unmappable byte sequences by substitution rather than throwing, then strips
     * {@link #INVALID_XML_CHARS} before handing the content to the parser.
     */
    private static String sanitizedXml(Path reportFile) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        String content = decoder.decode(ByteBuffer.wrap(Files.readAllBytes(reportFile))).toString();
        return INVALID_XML_CHARS.matcher(content).replaceAll("");
    }

    private static boolean hasChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(tagName)) {
                return true;
            }
        }
        return false;
    }
}
