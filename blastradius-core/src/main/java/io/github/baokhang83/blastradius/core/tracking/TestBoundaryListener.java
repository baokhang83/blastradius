package io.github.baokhang83.blastradius.core.tracking;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * A JUnit 5 {@link TestExecutionListener} that marks the currently-executing test via a
 * {@link ThreadLocal}, so {@link DependencyTrackingAgent}'s class-load observations can be
 * attributed to the test that triggered them.
 *
 * <p>Assumes tests execute sequentially on one thread per test (no parallel execution) —
 * a documented limitation of this Foundational slice, not a correctness issue for the
 * validator's target use case.
 */
public final class TestBoundaryListener implements TestExecutionListener {

    private static final ThreadLocal<TestIdentity> CURRENT_TEST = new ThreadLocal<>();

    /** The test currently executing on this thread, or {@code null} if none. */
    public static TestIdentity currentTest() {
        return CURRENT_TEST.get();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            CURRENT_TEST.set(toTestIdentity(testIdentifier));
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            CURRENT_TEST.remove();
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
