package io.github.baokhang83.blastradius.plugin.mojo;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Discovers every test in the target project's compiled test classes via real JUnit
 * Platform test discovery (not execution) — the same set Surefire would otherwise
 * discover and run in full, needed to know what exists *before* narrowing it.
 *
 * <p>Runs against a classloader built from the target project's own test classpath
 * (test-classes, main classes, and dependencies), not the plugin's own — a Maven Mojo's
 * default classloader doesn't include the project it's running against.
 */
public final class TestDiscoverer {

    public Set<TestIdentity> discoverAllTests(Path testClassesDir, List<String> testClasspathElements) {
        URL[] urls = testClasspathElements.stream().map(TestDiscoverer::toUrl).toArray(URL[]::new);
        ClassLoader discoveryClassLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(discoveryClassLoader);
        try {
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectClasspathRoots(Set.of(testClassesDir)))
                    .build();
            Launcher launcher = LauncherFactory.create();
            TestPlan testPlan = launcher.discover(request);

            Set<TestIdentity> result = new HashSet<>();
            for (TestIdentifier root : testPlan.getRoots()) {
                collectTests(testPlan, root, result);
            }
            return result;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static void collectTests(TestPlan plan, TestIdentifier identifier, Set<TestIdentity> out) {
        if (identifier.isTest()) {
            out.add(toTestIdentity(identifier));
        }
        for (TestIdentifier child : plan.getChildren(identifier)) {
            collectTests(plan, child, out);
        }
    }

    private static TestIdentity toTestIdentity(TestIdentifier identifier) {
        return identifier.getSource()
                .filter(MethodSource.class::isInstance)
                .map(MethodSource.class::cast)
                .map(source -> new TestIdentity(source.getClassName(), source.getMethodName()))
                .orElseGet(() -> new TestIdentity(identifier.getLegacyReportingName(), null));
    }

    private static URL toUrl(String path) {
        try {
            return Path.of(path).toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("invalid classpath element: " + path, e);
        }
    }
}
