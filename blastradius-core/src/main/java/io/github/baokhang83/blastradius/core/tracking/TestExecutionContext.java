package io.github.baokhang83.blastradius.core.tracking;

/**
 * JUnit-free handoff between the agent and the JUnit Platform listener.
 *
 * <p>The agent is installed in Maven's outer JVM as well as in Surefire forks. Maven itself does
 * not provide JUnit Platform classes, so its premain path must be able to ask for the current test
 * without loading {@link TestBoundaryListener}. A Surefire fork loads that listener later through
 * its own JUnit Platform service-provider interface and publishes boundaries here.
 */
final class TestExecutionContext {

    private static final InheritableThreadLocal<TestIdentity> CURRENT_TEST = new InheritableThreadLocal<>();

    private TestExecutionContext() {}

    static TestIdentity currentTest() {
        return CURRENT_TEST.get();
    }

    static void start(TestIdentity test) {
        CURRENT_TEST.set(test);
    }

    static void finish() {
        CURRENT_TEST.remove();
    }
}
