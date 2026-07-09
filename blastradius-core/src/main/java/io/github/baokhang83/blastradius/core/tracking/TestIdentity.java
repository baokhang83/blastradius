package io.github.baokhang83.blastradius.core.tracking;

import java.util.Objects;

/**
 * A stable identity for one test, used to correlate selection decisions with
 * ground-truth results across a commit pair.
 *
 * @param className  fully-qualified test class name
 * @param methodName test method name, or {@code null} for class-level identity
 */
public record TestIdentity(String className, String methodName) {

    public TestIdentity {
        Objects.requireNonNull(className, "className");
    }

    /**
     * The identity to use when looking up this test's tracked baseline dependencies.
     *
     * <p>For a {@code @ParameterizedTest} (or similar), Surefire's XML reports record
     * each invocation under a distinct name like {@code methodName(paramType)[1]}, while
     * the live JUnit 5 {@code TestExecutionListener} used during dependency tracking
     * reports the same underlying method's plain name for every invocation. Both are
     * correct for their own purpose (ground truth needs per-invocation pass/fail; the
     * agent naturally collapses to one dependency set per method) — but a lookup that
     * mixes the two would treat every parameterized invocation as having no baseline at
     * all. This is safe (falls back to always-selected) but destroys any real savings
     * signal for a project that uses parameterized tests. Stripping the trailing
     * {@code (...)}/{@code [...]} segments here lets a lookup key built from ground
     * truth find the baseline the tracking agent actually recorded.
     *
     * <p>Not used for ground-truth pass/fail comparison ({@code WouldMissComparator}),
     * which must stay per-invocation — collapsing that would risk masking a real
     * failing invocation behind a passing sibling.
     */
    public TestIdentity baselineKey() {
        if (methodName == null) {
            return this;
        }
        String normalized = methodName.replaceAll("\\(.*\\)", "").replaceAll("\\[.*\\]", "");
        return normalized.equals(methodName) ? this : new TestIdentity(className, normalized);
    }
}
