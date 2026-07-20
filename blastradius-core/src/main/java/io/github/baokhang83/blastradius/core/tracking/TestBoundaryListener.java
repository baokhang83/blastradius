package io.github.baokhang83.blastradius.core.tracking;

import java.util.Set;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * A JUnit 5 {@link TestExecutionListener} that marks the currently-executing test via a
 * {@link InheritableThreadLocal}, so {@link DependencyTrackingAgent}'s class-load observations can
 * be attributed to the test that triggered them, including classes first loaded by a child thread.
 *
 * <p>Tests must await child-thread work before they finish. The identity is copied when a child
 * thread is created, so work that outlives its test cannot be attributed reliably. Tests executing
 * in parallel remain outside this listener's supported model.
 */
public final class TestBoundaryListener implements TestExecutionListener {

    private static final InheritableThreadLocal<TestIdentity> CURRENT_TEST = new InheritableThreadLocal<>();
    private static final ThreadLocal<Set<Class<?>>> CLASSES_AT_TEST_START = new ThreadLocal<>();

    /** The test currently executing on this thread, or {@code null} if none. */
    public static TestIdentity currentTest() {
        return CURRENT_TEST.get();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            CURRENT_TEST.set(toTestIdentity(testIdentifier));
            CLASSES_AT_TEST_START.set(DependencyTrackingAgent.loadedClasses());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            TestIdentity currentTest = CURRENT_TEST.get();
            if (currentTest != null) {
                DependencyTrackingAgent.recordHiddenClassesLoadedSince(
                        currentTest, CLASSES_AT_TEST_START.get());
            }
            CURRENT_TEST.remove();
            CLASSES_AT_TEST_START.remove();
        }
    }

    private static TestIdentity toTestIdentity(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
                .filter(MethodSource.class::isInstance)
                .map(MethodSource.class::cast)
                .map(source -> new TestIdentity(source.getClassName(), source.getMethodName()))
                .orElseGet(() -> new TestIdentity(testIdentifier.getLegacyReportingName(), null));
    }
}
