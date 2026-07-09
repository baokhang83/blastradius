package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class SurefireFilterApplierTest {

    private final SurefireFilterApplier applier = new SurefireFilterApplier();

    @Test
    void setsTheTestPropertyToACommaSeparatedListOfSelectedTests() {
        MavenProject project = new MavenProject(new Model());
        TestIdentity fooTest = new TestIdentity("com.example.FooTest", "checksAdd");
        TestIdentity barTest = new TestIdentity("com.example.BarTest", "checksSubtract");

        applier.apply(project, Set.of(fooTest, barTest));

        String filter = project.getProperties().getProperty("test");
        List<String> patterns = Arrays.asList(filter.split(","));
        assertEquals(2, patterns.size());
        assertTrue(patterns.contains("com.example.FooTest#checksAdd"));
        assertTrue(patterns.contains("com.example.BarTest#checksSubtract"));
    }

    @Test
    void aClassLevelTestIdentityWithNoMethodNameSelectsTheWholeClass() {
        MavenProject project = new MavenProject(new Model());
        TestIdentity classLevel = new TestIdentity("com.example.FooTest", null);

        applier.apply(project, Set.of(classLevel));

        assertEquals("com.example.FooTest", project.getProperties().getProperty("test"));
    }
}
