package io.github.baokhang83.blastradius.plugin.report;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Reads per-test duration data from Surefire/Failsafe's standard JUnit XML reports. */
public final class SurefireReportReader {

    public Map<TestIdentity, Long> read(Path reportsDirectory) {
        if (!Files.isDirectory(reportsDirectory)) {
            return Map.of();
        }
        Map<TestIdentity, Long> timings = new LinkedHashMap<>();
        try (var files = Files.list(reportsDirectory)) {
            files.filter(path -> path.getFileName().toString().startsWith("TEST-"))
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .forEach(path -> readFile(path, timings));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list test reports in " + reportsDirectory, e);
        }
        return Map.copyOf(timings);
    }

    private static void readFile(Path report, Map<TestIdentity, Long> timings) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(report.toFile());
            var testCases = document.getElementsByTagName("testcase");
            for (int i = 0; i < testCases.getLength(); i++) {
                Element testCase = (Element) testCases.item(i);
                String className = testCase.getAttribute("classname");
                String methodName = testCase.getAttribute("name");
                if (className.isBlank() || methodName.isBlank()) {
                    continue;
                }
                timings.put(new TestIdentity(className, methodName), milliseconds(testCase.getAttribute("time")));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to parse test report " + report, e);
        }
    }

    private static long milliseconds(String seconds) {
        try {
            return Math.round(Double.parseDouble(seconds) * 1_000);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid test duration: " + seconds, e);
        }
    }
}
