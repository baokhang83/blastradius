package io.github.baokhang83.blastradius.core.reactor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The reactor's inter-module dependency graph, derived from the git tree's POM files
 * (never from Maven's runtime reactor — the shadow-mode validator has no live
 * {@code MavenProject}). Used to scope the conservative {@code NON_SOURCE} fallback: a change
 * inside module X can only affect X and the modules that (transitively) depend on X.
 *
 * <p>Immutable; build one per commit pair via {@link ReactorModuleGraphBuilder}.
 */
public final class ReactorModuleGraph {

    /** Modules with a real directory, longest {@code relativePath} first for deepest-match. */
    private final List<ModuleId> modulesByDepth;
    /** For each module, the set of modules that directly depend on it (reverse edges). */
    private final Map<ModuleId, Set<ModuleId>> directDependents;
    /** Repo-relative paths whose change affects every module (root aggregator / parent pom). */
    private final Set<String> reactorWidePaths;

    ReactorModuleGraph(
            List<ModuleId> modulesByDepth,
            Map<ModuleId, Set<ModuleId>> directDependents,
            Set<String> reactorWidePaths) {
        this.modulesByDepth = List.copyOf(modulesByDepth);
        this.directDependents = Map.copyOf(directDependents);
        this.reactorWidePaths = Set.copyOf(reactorWidePaths);
    }

    /**
     * The deepest module whose directory contains {@code repoRelativePath}, or empty if the
     * path lies under no known module (the caller must then treat it as reactor-wide — never
     * guess narrower).
     */
    public Optional<ModuleId> moduleOf(String repoRelativePath) {
        String path = normalize(repoRelativePath);
        for (ModuleId module : modulesByDepth) {
            if (containsPath(module, path)) {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }

    /**
     * {@code module} plus every module that transitively depends on it — the full set of
     * modules a change inside {@code module} could affect.
     */
    public Set<ModuleId> dependentsOf(ModuleId module) {
        Set<ModuleId> reached = new HashSet<>();
        collectDependents(module, reached);
        return reached;
    }

    /**
     * True when a change to {@code repoRelativePath} must select the whole suite — the root
     * aggregator pom or any file attributable to no single leaf module.
     */
    public boolean isReactorWide(String repoRelativePath) {
        String path = normalize(repoRelativePath);
        return reactorWidePaths.contains(path) || moduleOf(path).isEmpty();
    }

    private void collectDependents(ModuleId module, Set<ModuleId> reached) {
        if (!reached.add(module)) {
            return;
        }
        for (ModuleId dependent : directDependents.getOrDefault(module, Set.of())) {
            collectDependents(dependent, reached);
        }
    }

    private static boolean containsPath(ModuleId module, String path) {
        String dir = module.relativePath();
        if (dir.isEmpty()) {
            return true;
        }
        return path.equals(dir) || path.startsWith(dir + "/");
    }

    private static String normalize(String path) {
        String forward = path.replace('\\', '/');
        return forward.startsWith("./") ? forward.substring(2) : forward;
    }
}
