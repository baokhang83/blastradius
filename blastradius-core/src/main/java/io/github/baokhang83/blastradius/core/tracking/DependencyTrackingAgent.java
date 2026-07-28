package io.github.baokhang83.blastradius.core.tracking;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A {@code -javaagent} that observes every class loaded in the JVM it's attached to,
 * attributes each load to the currently-executing test (via the injected supplier — in
 * production, {@link TestBoundaryListener#currentTest()}), and records the class name with a
 * SHA-256 checksum of the loaded bytecode. A project class that discovery loaded before the first
 * test can be retransformed with runtime-use callbacks, so its later execution is attributed to
 * the test that actually uses it. Classes loaded while no test is running (JVM/Surefire bootstrap,
 * etc.) are retained as ambient when they cannot be safely instrumented. Hidden classes are not
 * class-loader definitions, so the listener uses the agent's loaded-class list at test boundaries
 * to record their stable source name. The persisted selection index consumes class names only.
 */
public final class DependencyTrackingAgent implements ClassFileTransformer {

    private static final String HIDDEN_CLASS_CHECKSUM = "hidden-class-bytecode-unavailable";
    private static final String TRACKING_PACKAGE_PREFIX =
            "io.github.baokhang83.blastradius.core.tracking.";

    /**
     * Set by {@code TrackRunner} on the forked Surefire JVM's command line. It names the reactor
     * root of the build being tracked, which is the only reliable way to tell a project class from
     * a third-party one: a module that depends on another reactor module loads it from that
     * module's built <em>jar</em>, not from its {@code target/classes} directory, so a
     * code-source path shape alone cannot recognise it.
     */
    private static final String PROJECT_ROOT_PROPERTY = "blastradius.projectRoot";

    /**
     * The bytecode library the agent instruments with. It ships inside the agent jar, so it must be
     * excluded by package: excluding the agent's own code source instead would be wrong, because a
     * module that depends on this one loads the agent classes from the ordinary core jar — the very
     * jar that carries the project classes we are trying to attribute.
     *
     * <p>The shaded blastradius-validator jar (the one actually attached via {@code -javaagent})
     * relocates {@code org.objectweb.asm} to keep it from colliding with a target project's own
     * classes; a string literal like this one is not bytecode-rewritten by the shade plugin, so it
     * must name the relocated package explicitly rather than the original one. blastradius-core's
     * own jar (used as an ordinary reactor dependency, not as the agent) never relocates ASM, so
     * this prefix would be wrong there — but this check only ever matters when running as the
     * agent, where the shaded prefix is the one actually in effect.
     */
    private static final String INSTRUMENTATION_LIBRARY_PACKAGE_PREFIX =
            "io.github.baokhang83.blastradius.shaded.asm.";

    /**
     * Found running against a real apache/shenyu build: computing a checksum here (touching
     * {@code MessageDigest} and {@code ConcurrentHashMap}) is reentrant class-loading work
     * happening in the middle of the JVM's own {@code MethodHandle}/{@code invokedynamic}
     * bootstrap sequence for a class shaped like this, which threw
     * {@link ClassCircularityError} once a second, dynamically self-attached javaagent
     * (Mockito's inline mock maker attaching its own byte-buddy agent at runtime) was also
     * active in the same fork and racing to instrument the same machinery. These classes are
     * pure JDK platform plumbing, identical for any two commits built with the same JDK, so
     * tracking their checksums has no test-selection value — skipping them removes a real
     * crash risk for free.
     */
    private static final String JDK_INVOKE_BOOTSTRAP_PACKAGE_PREFIX = "java.lang.invoke.";

    /**
     * Computing a checksum can initialize JDK security-provider classes. Those class loads call
     * this transformer again, so they must not recursively trigger another checksum computation.
     */
    private static final ThreadLocal<Boolean> RECORDING_CLASS_LOAD = ThreadLocal.withInitial(() -> false);

    /**
     * Initialized while the agent class is loaded, before {@link #premain(String, Instrumentation)}
     * registers the transformer. Security-provider initialization must never happen from within
     * {@link #transform(ClassLoader, String, Class, ProtectionDomain, byte[])}.
     */
    private static final MessageDigest SHA_256 = initializeSha256();
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * Resolved once, here, rather than freshly on every {@link #transform} call: it's a
     * {@code -D} flag fixed for the JVM's whole lifetime (set once, on the command line, by
     * {@code TrackRunner}), but resolving it touches the filesystem ({@link #canonicalize}
     * calls {@code toRealPath()}). Since every project-class fix below now runs this check on
     * every single class load in the JVM — not just once, before the first test — repeating
     * that syscall per class load would be a real cost for no benefit.
     */
    private static final Path CONFIGURED_PROJECT_ROOT = resolveConfiguredProjectRoot();

    private static volatile DependencyTrackingAgent installedAgent;
    private static volatile Instrumentation instrumentation;

    private final Supplier<TestIdentity> currentTestSupplier;
    private final Map<TestIdentity, Map<String, String>> checksumsByTest = new ConcurrentHashMap<>();
    private final Set<String> ambientDependencies = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingAmbientRetransformations = ConcurrentHashMap.newKeySet();
    private final Set<String> transformedAmbientClasses = ConcurrentHashMap.newKeySet();
    private final Map<String, String> ambientChecksums = new ConcurrentHashMap<>();
    private final AmbientClassInstrumenter ambientClassInstrumenter = new AmbientClassInstrumenter();
    private final AtomicBoolean ambientSnapshotTaken = new AtomicBoolean(false);

    public DependencyTrackingAgent() {
        this(TestBoundaryListener::currentTest);
    }

    /** Visible for testing: inject a fake "current test" source instead of the real listener. */
    DependencyTrackingAgent(Supplier<TestIdentity> currentTestSupplier) {
        this.currentTestSupplier = currentTestSupplier;
    }

    /**
     * Entry point for {@code -javaagent:agent.jar=<outputFilePathPrefix>}. If an output
     * path prefix is supplied, a shutdown hook writes the recorded dependencies to a
     * file unique to this JVM ({@code <prefix>.<pid>}) when the JVM exits — necessary
     * because the agent runs in a subprocess a parent process can only inspect after
     * that subprocess has fully exited (research.md #1). {@link DependencyRecordReader}
     * merges every sibling file back into one map once the whole build has finished.
     *
     * <p>Attachment is done via {@code JAVA_TOOL_OPTIONS} (see {@code MavenBuildRunner}),
     * which every JVM launch picks up — including the outer {@code mvn} process itself,
     * not just the forked Surefire JVM(s) that actually run tests, and — for target
     * projects configured with {@code reuseForks=false} — every one of the many
     * sequential per-test-class JVMs Surefire spawns. A per-JVM file (rather than one
     * shared file with a read-merge-write on every shutdown) avoids a real race: Surefire
     * does not wait for a fork's OS process to fully exit before starting the next one,
     * so two sibling JVMs' shutdown hooks can genuinely overlap, and a shared file left
     * only the last few writers' data intact once one hook's write raced another's read.
     * A JVM that recorded no tests (e.g. the outer {@code mvn} process) writes nothing.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        DependencyTrackingAgent agent = new DependencyTrackingAgent();
        installedAgent = agent;
        instrumentation = inst;
        inst.addTransformer(agent, true);
        if (agentArgs != null && !agentArgs.isBlank()) {
            Path outputFile = Path.of(agentArgs + "." + ProcessHandle.current().pid());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> runShutdownHook(
                    outputFile, agent::recordedDependencies, agent::ambientDependencies,
                    new DependencyRecordWriter())));
        }
    }

    /**
     * The shutdown hook's actual work, factored out so a test can drive it with a
     * deliberately-throwing supplier instead of forking a real JVM. Catches
     * {@link Throwable}, not just {@link Exception}: the failure that motivated this
     * (found running against apache/shenyu) was a {@link ClassCircularityError} — an
     * unrelated javaagent's JDK-version mismatch corrupting class loading inside this
     * JVM — which silently killed the shutdown-hook thread before it could write
     * anything, leaving no trace beyond a build log nobody reads on success. A crash
     * marker instead gives {@link DependencyRecordReader} a concrete reason to report.
     */
    static void runShutdownHook(Path outputFile, Supplier<Map<TestIdentity, Map<String, String>>> recordedDependencies,
            Supplier<Set<String>> ambientDependencies, DependencyRecordWriter writer) {
        try {
            Map<TestIdentity, Map<String, String>> recorded = recordedDependencies.get();
            if (!recorded.isEmpty()) {
                writer.write(outputFile, recorded, ambientDependencies.get());
            }
        } catch (Throwable t) {
            try {
                writer.writeCrashMarker(outputFile, t);
            } catch (Throwable ignored) {
                // Best effort: a failure here must not itself crash the JVM's shutdown sequence.
            }
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null || RECORDING_CLASS_LOAD.get()) {
            return null;
        }

        String dottedClassName = className.replace('/', '.');
        if (dottedClassName.startsWith(JDK_INVOKE_BOOTSTRAP_PACKAGE_PREFIX)) {
            return null;
        }
        if (classBeingRedefined != null && pendingAmbientRetransformations.contains(dottedClassName)) {
            try {
                byte[] instrumented = ambientClassInstrumenter.instrument(dottedClassName, classfileBuffer);
                if (instrumented != null) {
                    ambientChecksums.put(dottedClassName, sha256Hex(classfileBuffer));
                    transformedAmbientClasses.add(dottedClassName);
                }
                return instrumented;
            } catch (RuntimeException ignored) {
                // An uninstrumentable discovery-loaded class stays ambient and therefore safe.
                return null;
            }
        }

        TestIdentity currentTest = currentTestSupplier.get();
        boolean isProjectClass = isAmbientInstrumentationCandidate(dottedClassName, protectionDomain);
        if (currentTest == null && !isProjectClass) {
            return null;
        }

        RECORDING_CLASS_LOAD.set(true);
        try {
            String checksum = sha256Hex(classfileBuffer);
            byte[] instrumented = null;
            if (isProjectClass) {
                // Every project class gets this at its first (and only) load — not just the
                // ones the pre-first-test snapshot happens to catch — so a later test that
                // reuses an already-loaded instance (e.g. a cached Spring bean) still gets
                // attributed via the injected callback instead of silently missing it.
                try {
                    instrumented = ambientClassInstrumenter.instrument(dottedClassName, classfileBuffer);
                    if (instrumented != null) {
                        ambientChecksums.put(dottedClassName, checksum);
                    }
                } catch (RuntimeException ignored) {
                    // Stays uninstrumented, same as any class the instrumenter can't rewrite.
                }
            }
            if (currentTest != null) {
                checksumsByTest
                        .computeIfAbsent(currentTest, ignored -> new ConcurrentHashMap<>())
                        .put(dottedClassName, checksum);
            }
            return instrumented;
        } finally {
            RECORDING_CLASS_LOAD.remove();
        }
    }

    /**
     * Records that a test executed even if it never causes a class-load event while it is active.
     *
     * <p>An empty dependency map is meaningful: it is a baseline for an existing test, not an
     * absent baseline for a newly added test. Selection must preserve that distinction or it
     * conservatively re-runs every test whose dependencies happened to be loaded before the test
     * began.
     */
    static void recordTestStarted(TestIdentity test) {
        DependencyTrackingAgent currentAgent = installedAgent;
        if (currentAgent != null) {
            currentAgent.recordTestExecution(test);
        }
    }

    void recordTestExecution(TestIdentity test) {
        checksumsByTest.computeIfAbsent(test, ignored -> new ConcurrentHashMap<>());
    }

    /**
     * Called once, before the very first test in a fork starts (see {@link TestBoundaryListener}).
     * Everything already loaded at that point — JVM/Surefire bootstrap, JUnit Platform's
     * discovery pass walking every test class's signatures — got exactly one, unattributed
     * {@link #transform} call each and can never be re-observed, so no single test can be
     * blamed for depending on them. Recording them as fork-wide {@code ambientDependencies}
     * lets selection fall back rather than silently reporting {@code NO_MATCH} when one of
     * them changes. Idempotent: only the first caller's snapshot is taken.
     */
    static void recordAmbientSnapshot() {
        DependencyTrackingAgent currentAgent = installedAgent;
        if (currentAgent != null) {
            currentAgent.snapshotAmbientDependencies();
        }
    }

    void snapshotAmbientDependencies() {
        if (ambientSnapshotTaken.compareAndSet(false, true)) {
            Set<Class<?>> loadedClasses = loadedClasses();
            for (Class<?> loadedClass : loadedClasses) {
                ambientDependencies.add(loadedClass.getName());
            }
            retransformProjectAmbientClasses(loadedClasses);
        }
    }

    /** Called from bytecode injected into an already-loaded project class. */
    public static void recordAmbientExecution(String className) {
        DependencyTrackingAgent currentAgent = installedAgent;
        if (currentAgent != null) {
            currentAgent.recordAmbientExecutionForCurrentTest(className);
        }
    }

    private void recordAmbientExecutionForCurrentTest(String className) {
        TestIdentity currentTest = currentTestSupplier.get();
        String checksum = ambientChecksums.get(className);
        if (currentTest != null && checksum != null) {
            checksumsByTest
                    .computeIfAbsent(currentTest, ignored -> new ConcurrentHashMap<>())
                    .putIfAbsent(className, checksum);
        }
    }

    private void retransformProjectAmbientClasses(Set<Class<?>> loadedClasses) {
        Instrumentation currentInstrumentation = instrumentation;
        if (currentInstrumentation == null || !currentInstrumentation.isRetransformClassesSupported()) {
            return;
        }
        Path projectRoot = configuredProjectRoot();
        for (Class<?> loadedClass : loadedClasses) {
            String className = loadedClass.getName();
            if (ambientChecksums.containsKey(className)) {
                // Already instrumented at its original class-load (see #transform): this
                // snapshot pass now mostly only catches what that inline path missed.
                // Retransforming it again here would double-inject the runtime-use callback.
                ambientDependencies.remove(className);
                continue;
            }
            if (!isAmbientInstrumentationCandidate(loadedClass, projectRoot)
                    || !currentInstrumentation.isModifiableClass(loadedClass)) {
                continue;
            }
            pendingAmbientRetransformations.add(className);
            try {
                currentInstrumentation.retransformClasses(loadedClass);
                if (transformedAmbientClasses.remove(className)) {
                    ambientDependencies.remove(className);
                }
            } catch (Exception | LinkageError ignored) {
                // Retain the ambient class so selection continues to use its safe fallback.
            } finally {
                pendingAmbientRetransformations.remove(className);
            }
        }
    }

    static boolean isAmbientInstrumentationCandidate(Class<?> loadedClass) {
        return isAmbientInstrumentationCandidate(loadedClass, configuredProjectRoot());
    }

    static boolean isAmbientInstrumentationCandidate(Class<?> loadedClass, Path projectRoot) {
        if (loadedClass.getName().startsWith(TRACKING_PACKAGE_PREFIX)
                || loadedClass.getName().startsWith(INSTRUMENTATION_LIBRARY_PACKAGE_PREFIX)
                || loadedClass.isArray()
                || loadedClass.isPrimitive()) {
            return false;
        }
        Path codeSource = codeSourceOf(loadedClass);
        return codeSource != null && isProjectCodeSource(codeSource, projectRoot);
    }

    /**
     * Same test as {@link #isAmbientInstrumentationCandidate(Class, Path)}, but usable from
     * {@link #transform} itself: a class being defined has no {@link Class} object yet, only
     * the {@link ProtectionDomain} the JVM hands the transformer directly. Array and primitive
     * types never reach {@link #transform} (they're never defined from a classfile buffer), so
     * unlike the {@code Class}-based check, this one doesn't need to filter them out.
     */
    private static boolean isAmbientInstrumentationCandidate(String dottedClassName, ProtectionDomain protectionDomain) {
        if (dottedClassName.startsWith(TRACKING_PACKAGE_PREFIX)
                || dottedClassName.startsWith(INSTRUMENTATION_LIBRARY_PACKAGE_PREFIX)) {
            return false;
        }
        Path codeSource = codeSourceOf(protectionDomain);
        return codeSource != null && isProjectCodeSource(codeSource, CONFIGURED_PROJECT_ROOT);
    }

    /**
     * A class belongs to the build under test when its code source lives under the reactor root —
     * whether that is a module's {@code target/classes} directory or another module's built jar,
     * which is how every downstream module sees its reactor dependencies. Without the reactor root
     * the only safe answer is the class-output directory shape: a jar cannot then be told apart
     * from a third-party one, so it stays ambient and selection keeps its conservative fallback.
     */
    static boolean isProjectCodeSource(Path codeSource, Path projectRoot) {
        if (projectRoot != null && codeSource.startsWith(projectRoot)) {
            return true;
        }
        String path = codeSource.toString().replace('\\', '/');
        return path.endsWith("/target/classes")
                || path.endsWith("/target/test-classes")
                || path.endsWith("/build/classes/java/main")
                || path.endsWith("/build/classes/java/test");
    }

    private static Path configuredProjectRoot() {
        return CONFIGURED_PROJECT_ROOT;
    }

    private static Path resolveConfiguredProjectRoot() {
        String configured = System.getProperty(PROJECT_ROOT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            return canonicalize(Path.of(configured));
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    /**
     * Resolves symlinks so the reactor root and a code source under it stay comparable. They
     * routinely disagree otherwise — a macOS temp directory is handed to the build as
     * {@code /var/...} but reported by the class loader as {@code /private/var/...}, and a
     * containment check between the two forms silently fails.
     */
    private static Path canonicalize(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return absolute.toRealPath();
        } catch (IOException ignored) {
            return absolute;
        }
    }

    private static Path codeSourceOf(Class<?> loadedClass) {
        return codeSourceOf(loadedClass.getProtectionDomain());
    }

    private static Path codeSourceOf(ProtectionDomain protectionDomain) {
        if (protectionDomain == null || protectionDomain.getCodeSource() == null
                || protectionDomain.getCodeSource().getLocation() == null) {
            return null;
        }
        try {
            URI location = protectionDomain.getCodeSource().getLocation().toURI();
            if (!"file".equals(location.getScheme())) {
                return null;
            }
            return canonicalize(Path.of(location));
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return null;
        }
    }

    /** An immutable snapshot of {@code test -> {className -> tracking token}} recorded so far. */
    public Map<TestIdentity, Map<String, String>> recordedDependencies() {
        return checksumsByTest.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> Map.copyOf(e.getValue())));
    }

    /** Class names loaded before the first test's tracking window opened in this fork. */
    public Set<String> ambientDependencies() {
        return Set.copyOf(ambientDependencies);
    }

    static Set<Class<?>> loadedClasses() {
        Instrumentation currentInstrumentation = instrumentation;
        if (currentInstrumentation == null) {
            return Set.of();
        }
        return Arrays.stream(currentInstrumentation.getAllLoadedClasses())
                .<Class<?>>map(loadedClass -> loadedClass)
                .collect(Collectors.toUnmodifiableSet());
    }

    static void recordHiddenClassesLoadedSince(TestIdentity test, Set<Class<?>> classesAtTestStart) {
        DependencyTrackingAgent currentAgent = installedAgent;
        Instrumentation currentInstrumentation = instrumentation;
        if (currentAgent != null && currentInstrumentation != null) {
            currentAgent.recordNewHiddenClasses(test, classesAtTestStart, currentInstrumentation.getAllLoadedClasses());
        }
    }

    void recordNewHiddenClasses(TestIdentity test, Set<Class<?>> classesAtTestStart, Class<?>[] allLoadedClasses) {
        Set<Class<?>> baseline = classesAtTestStart == null ? Set.of() : classesAtTestStart;
        for (Class<?> loadedClass : allLoadedClasses) {
            if (loadedClass.isHidden() && !baseline.contains(loadedClass)) {
                String sourceClassName = sourceClassName(loadedClass);
                if (sourceClassName != null) {
                    checksumsByTest
                            .computeIfAbsent(test, ignored -> new ConcurrentHashMap<>())
                            .putIfAbsent(sourceClassName, HIDDEN_CLASS_CHECKSUM);
                }
            }
        }
    }

    private static String sha256Hex(byte[] bytes) {
        byte[] hash;
        synchronized (SHA_256) {
            hash = SHA_256.digest(bytes);
        }
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            int unsigned = Byte.toUnsignedInt(b);
            sb.append(HEX_DIGITS[unsigned >>> 4]);
            sb.append(HEX_DIGITS[unsigned & 0x0f]);
        }
        return sb.toString();
    }

    private static MessageDigest initializeSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
        }
    }

    private static String sourceClassName(Class<?> hiddenClass) {
        String hiddenName = hiddenClass.getName();
        int suffixSeparator = hiddenName.lastIndexOf('/');
        return suffixSeparator > 0 ? hiddenName.substring(0, suffixSeparator) : null;
    }
}
