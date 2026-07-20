package io.github.baokhang83.blastradius.gradle;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

final class TestDiscoverer {

    Set<TestIdentity> discoverAllTests(Collection<java.io.File> classpath, Collection<java.io.File> testClassesDirs) {
        URL[] urls = classpath.stream().map(TestDiscoverer::toUrl).toArray(URL[]::new);
        Set<Path> testClassRoots = testClassesDirs.stream()
                .map(java.io.File::toPath)
                .collect(java.util.stream.Collectors.toSet());
        try (URLClassLoader discoveryClassLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader())) {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(discoveryClassLoader);
            try {
                LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectClasspathRoots(testClassRoots))
                        .build();
                TestPlan testPlan = LauncherFactory.create().discover(request);
                Set<TestIdentity> result = new HashSet<>();
                for (TestIdentifier root : testPlan.getRoots()) {
                    collectTests(testPlan, root, result);
                }
                return result;
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to close test discovery classloader", e);
        }
    }

    private static void collectTests(TestPlan plan, TestIdentifier identifier, Set<TestIdentity> out) {
        if (identifier.isTest()) {
            out.add(identifier.getSource()
                    .filter(MethodSource.class::isInstance)
                    .map(MethodSource.class::cast)
                    .map(source -> new TestIdentity(source.getClassName(), source.getMethodName()))
                    .orElseGet(() -> new TestIdentity(identifier.getLegacyReportingName(), null)));
        }
        for (TestIdentifier child : plan.getChildren(identifier)) {
            collectTests(plan, child, out);
        }
    }

    private static URL toUrl(java.io.File file) {
        try {
            return file.toPath().toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("invalid test classpath element: " + file, e);
        }
    }
}
