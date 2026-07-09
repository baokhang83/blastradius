package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurefireReportParserTest {

    private final SurefireReportParser parser = new SurefireReportParser();

    @Test
    void passedAndFailedTestCasesAreParsedCorrectly(@TempDir Path reportsDir) throws Exception {
        writeReport(reportsDir, "TEST-com.example.FooTest.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.FooTest" tests="2" errors="0" skipped="0" failures="1">
                    <testcase name="checksAdd" classname="com.example.FooTest" time="0.01"/>
                    <testcase name="checksSubtract" classname="com.example.FooTest" time="0.02">
                        <failure message="expected 5 but was 4" type="org.opentest4j.AssertionFailedError">stack trace here</failure>
                    </testcase>
                </testsuite>
                """);

        Map<TestIdentity, Boolean> results = parser.parse(reportsDir);

        assertTrue(results.get(new TestIdentity("com.example.FooTest", "checksAdd")));
        assertFalse(results.get(new TestIdentity("com.example.FooTest", "checksSubtract")));
    }

    @Test
    void errorElementAlsoCountsAsFailed(@TempDir Path reportsDir) throws Exception {
        writeReport(reportsDir, "TEST-com.example.BarTest.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.BarTest" tests="1" errors="1" skipped="0" failures="0">
                    <testcase name="throwsUnexpectedly" classname="com.example.BarTest" time="0.01">
                        <error message="boom" type="java.lang.RuntimeException">stack trace here</error>
                    </testcase>
                </testsuite>
                """);

        Map<TestIdentity, Boolean> results = parser.parse(reportsDir);

        assertFalse(results.get(new TestIdentity("com.example.BarTest", "throwsUnexpectedly")));
    }

    @Test
    void skippedTestCasesAreExcludedFromResults(@TempDir Path reportsDir) throws Exception {
        writeReport(reportsDir, "TEST-com.example.BazTest.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.BazTest" tests="1" errors="0" skipped="1" failures="0">
                    <testcase name="notRunYet" classname="com.example.BazTest" time="0.0">
                        <skipped/>
                    </testcase>
                </testsuite>
                """);

        Map<TestIdentity, Boolean> results = parser.parse(reportsDir);

        assertFalse(results.containsKey(new TestIdentity("com.example.BazTest", "notRunYet")));
    }

    @Test
    void multipleReportFilesAreAllParsed(@TempDir Path reportsDir) throws Exception {
        writeReport(reportsDir, "TEST-com.example.FooTest.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.FooTest" tests="1" errors="0" skipped="0" failures="0">
                    <testcase name="a" classname="com.example.FooTest" time="0.01"/>
                </testsuite>
                """);
        writeReport(reportsDir, "TEST-com.example.BarTest.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="com.example.BarTest" tests="1" errors="0" skipped="0" failures="0">
                    <testcase name="b" classname="com.example.BarTest" time="0.01"/>
                </testsuite>
                """);

        Map<TestIdentity, Boolean> results = parser.parse(reportsDir);

        assertEquals(2, results.size());
    }

    @Test
    void missingReportsDirectoryYieldsEmptyResults(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("does-not-exist");
        Map<TestIdentity, Boolean> results = parser.parse(nonExistent);
        assertTrue(results.isEmpty());
    }

    private static void writeReport(Path dir, String filename, String content) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(filename), content, StandardCharsets.UTF_8);
    }
}
