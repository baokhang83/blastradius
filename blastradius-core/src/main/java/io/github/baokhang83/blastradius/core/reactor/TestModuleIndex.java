package io.github.baokhang83.blastradius.core.reactor;

import io.github.baokhang83.blastradius.core.tracking.TestIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps a {@link TestIdentity} (a class FQN with no path) to its owning reactor module, by
 * scanning each module's test-source roots ({@code src/test/java}, {@code src/test/kotlin})
 * once per commit pair and attributing every discovered source file through the same
 * {@link ReactorModuleGraph#moduleOf(String)} the changed-file side uses.
 *
 * <p>This resolves the design's open decision (option 1, package-root scan). It is derived
 * purely from the git tree, so it works identically for the Maven plugin and the shadow-mode
 * validator's historical replay &mdash; neither has a live {@code MavenProject}. A class not
 * found here resolves to empty, and the caller must then treat the test as reactor-wide
 * (never guess it into a narrower scope).
 */
public final class TestModuleIndex {

    private final Map<String, ModuleId> moduleByClassName;

    private TestModuleIndex(Map<String, ModuleId> moduleByClassName) {
        this.moduleByClassName = Map.copyOf(moduleByClassName);
    }

    /**
     * The module owning {@code test}'s class, or empty if the class was found under no
     * module's test-source root (caller falls back to the whole suite).
     */
    public Optional<ModuleId> moduleOf(TestIdentity test) {
        return Optional.ofNullable(moduleByClassName.get(test.className()));
    }

    public static TestModuleIndex fromRepoTree(Path repoRoot, ReactorModuleGraph graph) {
        Map<String, ModuleId> byClassName = new HashMap<>();
        try {
            // walkFileTree, not Files.walk: prune .git / target / build whole-subtree rather
            // than walking every file and post-filtering (isTestSource still excludes any
            // build output that slips through). On a big repo the .git object store alone
            // dwarfs the source tree, so not descending it is the win.
            Files.walkFileTree(repoRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (".git".equals(name) || "target".equals(name) || "build".equals(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) {
                    if (!isTestSource(sourceFile)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String relative = repoRoot.relativize(sourceFile).toString().replace('\\', '/');
                    String className = classNameOf(relative);
                    if (className != null) {
                        graph.moduleOf(relative).ifPresent(module -> byClassName.put(className, module));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to scan test sources under " + repoRoot, e);
        }
        return new TestModuleIndex(byClassName);
    }

    private static boolean isTestSource(Path file) {
        String path = file.toString().replace('\\', '/');
        if (path.contains("/target/") || path.contains("/build/")) {
            return false;
        }
        boolean underTestRoot = path.contains("/src/test/java/") || path.contains("/src/test/kotlin/");
        return underTestRoot && (path.endsWith(".java") || path.endsWith(".kt"));
    }

    /** Derive the FQN from a test-source path by stripping the {@code src/test/{java,kotlin}/} root. */
    private static String classNameOf(String relativePath) {
        int javaRoot = relativePath.indexOf("src/test/java/");
        int kotlinRoot = relativePath.indexOf("src/test/kotlin/");
        int start;
        int rootLength;
        if (javaRoot >= 0) {
            start = javaRoot;
            rootLength = "src/test/java/".length();
        } else if (kotlinRoot >= 0) {
            start = kotlinRoot;
            rootLength = "src/test/kotlin/".length();
        } else {
            return null;
        }
        String packagePath = relativePath.substring(start + rootLength);
        int dot = packagePath.lastIndexOf('.');
        String withoutExtension = dot >= 0 ? packagePath.substring(0, dot) : packagePath;
        return withoutExtension.replace('/', '.');
    }
}
