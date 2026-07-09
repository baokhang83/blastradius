package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.testsupport.FixtureProjectBuilder;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndex;
import io.github.baokhang83.blastradius.plugin.index.DependencyIndexWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end: a real 2-module Maven reactor (tasks.md T026), with {@code
 * blastradius-maven-plugin} bound in both modules' {@code pom.xml} (the goal executes once
 * per reactor module, per Maven's own model). A single dependency index, tracked once
 * across the whole reactor (mirroring how a real {@code TRACK} run — or here, a direct
 * {@link EndToEndTestSupport#trackDependencies} call reusing the exact mechanism
 * {@code TrackRunner} uses — would produce it), is persisted once at the reactor root.
 * Both modules' own goal executions must resolve that same shared index rather than each
 * looking for one relative to their own basedir (FR-010) — this is the multi-module gap
 * left open by {@link SelectModeEndToEndTest}, which only exercises a single module.
 */
class SelectModeMultiModuleEndToEndTest {

    @BeforeAll
    static void installThisPluginOnce() throws Exception {
        EndToEndTestSupport.installThisPluginOnce();
    }

    @Test
    void crossModuleAttributionAppliesADistinctFilterPerModule(@TempDir Path projectDir) throws Exception {
        FixtureProjectBuilder fixture = FixtureProjectBuilder.twoModuleReactor(projectDir);
        fixture.ignoreTargetDirectory();

        // moduleA: Foo (will change) + its own direct test; Qux + its own unrelated test.
        fixture.writeClassInModule("moduleA", "com.example.a.Foo",
                "package com.example.a; public class Foo { public int value() { return 1; } }");
        fixture.writeTestInModule("moduleA", "com.example.a.FooTest", """
                package com.example.a;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void checksFoo() { assertEquals(1, new Foo().value()); }
                }
                """);
        fixture.writeClassInModule("moduleA", "com.example.a.Qux",
                "package com.example.a; public class Qux { public int value() { return 10; } }");
        fixture.writeTestInModule("moduleA", "com.example.a.QuxTest", """
                package com.example.a;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class QuxTest {
                    @Test
                    void checksQux() { assertEquals(10, new Qux().value()); }
                }
                """);

        // moduleB: Bar + its own unrelated test; UsesFooTest depends on moduleA's Foo
        // directly (cross-module attribution), with no local production class of its own.
        fixture.writeClassInModule("moduleB", "com.example.b.Bar",
                "package com.example.b; public class Bar { public int value() { return 2; } }");
        fixture.writeTestInModule("moduleB", "com.example.b.BarTest", """
                package com.example.b;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class BarTest {
                    @Test
                    void checksBar() { assertEquals(2, new Bar().value()); }
                }
                """);
        fixture.writeTestInModule("moduleB", "com.example.b.UsesFooTest", """
                package com.example.b;
                import com.example.a.Foo;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class UsesFooTest {
                    @Test
                    void checksFoo() { assertEquals(1, new Foo().value()); }
                }
                """);
        String anchorCommit = fixture.commit("initial");

        // Track once across the whole reactor (the agent is module-agnostic — README's
        // "cross-module attribution for free" claim), producing one shared index.
        DependencyIndex index = EndToEndTestSupport.trackDependencies(projectDir, anchorCommit);
        new DependencyIndexWriter().write(projectDir.resolve(".blastradius/index.json"), index);

        // A small, contained change: only Foo — value AND both its dependents' expectations
        // updated consistently.
        fixture.writeClassInModule("moduleA", "com.example.a.Foo",
                "package com.example.a; public class Foo { public int value() { return 99; } }");
        fixture.writeTestInModule("moduleA", "com.example.a.FooTest", """
                package com.example.a;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class FooTest {
                    @Test
                    void checksFoo() { assertEquals(99, new Foo().value()); }
                }
                """);
        fixture.writeTestInModule("moduleB", "com.example.b.UsesFooTest", """
                package com.example.b;
                import com.example.a.Foo;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class UsesFooTest {
                    @Test
                    void checksFoo() { assertEquals(99, new Foo().value()); }
                }
                """);
        fixture.commit("change Foo");

        String pluginXml = EndToEndTestSupport.pluginXml(anchorCommit);
        fixture.addBuildPlugin("moduleA", pluginXml);
        fixture.addBuildPlugin("moduleB", pluginXml);

        String output = EndToEndTestSupport.runMvnTest(projectDir);

        assertTrue(output.contains("BUILD SUCCESS"), "expected the build to succeed:\n" + output);
        assertTrue(output.contains("Running com.example.a.FooTest"),
                "expected moduleA's own dependent test to run:\n" + output);
        assertFalse(output.contains("Running com.example.a.QuxTest"),
                "expected moduleA's unrelated test to be skipped:\n" + output);
        assertTrue(output.contains("Running com.example.b.UsesFooTest"),
                "expected moduleB's cross-module-dependent test to run:\n" + output);
        assertFalse(output.contains("Running com.example.b.BarTest"),
                "expected moduleB's unrelated test to be skipped:\n" + output);
    }
}
