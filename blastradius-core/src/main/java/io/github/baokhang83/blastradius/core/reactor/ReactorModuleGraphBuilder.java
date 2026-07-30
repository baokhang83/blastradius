package io.github.baokhang83.blastradius.core.reactor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Builds a {@link ReactorModuleGraph} by walking a repository tree for {@code pom.xml} files
 * and parsing each one's coordinates, dependencies, and parent — all from disk, so it works
 * identically for the Maven plugin (working tree) and the validator (a checked-out scratch
 * copy). Never consults Maven's runtime model.
 */
public final class ReactorModuleGraphBuilder {

    /** One parsed POM: where it lives and which artifacts it references. */
    private record PomInfo(
            String artifactId, String relativePath, boolean aggregator, Set<String> referencedArtifactIds) {}

    public ReactorModuleGraph fromRepoTree(Path repoRoot) {
        List<PomInfo> poms = parseAllPoms(repoRoot);

        Map<String, ModuleId> byArtifactId = new HashMap<>();
        List<ModuleId> leafModules = new ArrayList<>();
        Set<String> reactorWidePaths = new HashSet<>();
        for (PomInfo pom : poms) {
            ModuleId id = new ModuleId(pom.artifactId(), pom.relativePath());
            byArtifactId.put(pom.artifactId(), id);
            // The reactor-root pom, and any file under no leaf module, is reactor-wide (handled
            // in ReactorModuleGraph.isReactorWide via an empty moduleOf). A module is only a
            // fallback-scoping unit if it has a real directory to attribute files to.
            if (!pom.relativePath().isEmpty()) {
                leafModules.add(id);
            } else {
                reactorWidePaths.add(pomPath(pom.relativePath()));
            }
        }

        // Reverse edges: for each module referencing artifact R, R -> referrer is a dependent.
        Map<ModuleId, Set<ModuleId>> directDependents = new HashMap<>();
        for (PomInfo pom : poms) {
            ModuleId referrer = byArtifactId.get(pom.artifactId());
            for (String referenced : pom.referencedArtifactIds()) {
                ModuleId target = byArtifactId.get(referenced);
                if (target != null && !target.equals(referrer)) {
                    directDependents.computeIfAbsent(target, ignored -> new HashSet<>()).add(referrer);
                }
            }
        }

        List<ModuleId> byDepth = new ArrayList<>(leafModules);
        byDepth.sort(Comparator.comparingInt((ModuleId m) -> m.relativePath().length()).reversed());
        return new ReactorModuleGraph(byDepth, directDependents, reactorWidePaths);
    }

    private static List<PomInfo> parseAllPoms(Path repoRoot) {
        DocumentBuilder documentBuilder = newDocumentBuilder();
        List<Path> pomFiles = new ArrayList<>();
        try {
            // walkFileTree, not Files.walk: pruning .git / target / build whole-subtree here
            // (SKIP_SUBTREE) avoids descending the repo's largest directories — on a big repo
            // the .git object store alone dwarfs the source tree — instead of walking every
            // file and post-filtering. build/target are skipped for the same reason the old
            // isUnderBuildOutput filter excluded them: a pom.xml copied into build output is
            // not a reactor module.
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
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName() != null && file.getFileName().toString().equals("pom.xml")) {
                        pomFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to walk repository tree at " + repoRoot, e);
        }
        List<PomInfo> result = new ArrayList<>();
        for (Path pomFile : pomFiles) {
            result.add(parsePom(documentBuilder, repoRoot, pomFile));
        }
        return result;
    }

    private static PomInfo parsePom(DocumentBuilder documentBuilder, Path repoRoot, Path pomFile) {
        try {
            Document doc = documentBuilder.parse(pomFile.toFile());
            doc.getDocumentElement().normalize();
            Element project = doc.getDocumentElement();

            String artifactId = childText(project, "artifactId");
            String packaging = childTextOrDefault(project, "packaging", "jar");
            String relativePath = toRelativePath(repoRoot, pomFile.getParent());

            Set<String> referenced = new HashSet<>();
            referenced.addAll(dependencyArtifactIds(project));
            parentArtifactId(project).ifPresent(referenced::add);

            return new PomInfo(artifactId, relativePath, "pom".equals(packaging), referenced);
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse pom " + pomFile, e);
        }
    }

    private static List<String> dependencyArtifactIds(Element project) {
        Element dependencies = firstChildElement(project, "dependencies");
        if (dependencies == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        NodeList deps = dependencies.getElementsByTagName("dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            if (deps.item(i) instanceof Element dep) {
                String id = childText(dep, "artifactId");
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static java.util.Optional<String> parentArtifactId(Element project) {
        Element parent = firstChildElement(project, "parent");
        return parent == null ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(childText(parent, "artifactId"));
    }

    /** Direct-child element text, ignoring same-named descendants (e.g. dependency/artifactId). */
    private static String childText(Element parent, String tag) {
        Element child = firstChildElement(parent, tag);
        return child == null ? null : child.getTextContent().trim();
    }

    private static String childTextOrDefault(Element parent, String tag, String fallback) {
        String value = childText(parent, tag);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static Element firstChildElement(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tag)) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String toRelativePath(Path repoRoot, Path moduleDir) {
        String relative = repoRoot.relativize(moduleDir).toString().replace('\\', '/');
        return relative.equals(".") ? "" : relative;
    }

    private static String pomPath(String moduleRelativePath) {
        return moduleRelativePath.isEmpty() ? "pom.xml" : moduleRelativePath + "/pom.xml";
    }

    private static DocumentBuilder newDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("failed to configure XML parser", e);
        }
    }
}
