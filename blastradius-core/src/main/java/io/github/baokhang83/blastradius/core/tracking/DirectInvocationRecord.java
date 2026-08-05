package io.github.baokhang83.blastradius.core.tracking;

import java.util.Map;
import java.util.Set;

/** One test's executed source classes and their declared direct invocation-owner classes. */
public record DirectInvocationRecord(TestIdentity test, Map<String, Set<String>> sourceToTargetClasses) {}
