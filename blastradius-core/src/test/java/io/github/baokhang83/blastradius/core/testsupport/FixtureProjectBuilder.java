package io.github.baokhang83.blastradius.core.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Programmatically scaffolds a minimal Maven/JUnit-5 project (single-module or a
 * 2-module reactor) into a directory and commits successive changes via JGit.
 *
 * <p>Test-support code, not engine code — not held to the constitution's TDD-first rule
 * (there is no test class this class itself makes pass). It exists so tests can
 * construct exact dependency/change scenarios without a checked-in nested git repo.
 */
public final class FixtureProjectBuilder {

    private static final PersonIdent AUTHOR = new PersonIdent("fixture", "fixture@example.invalid");

    private final Path root;
    private final Git git;

    private FixtureProjectBuilder(Path root, Git git) {
        this.root = root;
        this.git = git;
    }

    /** A single-module Maven/JUnit-5 project rooted at {@code root}, with git initialized. */
    public static FixtureProjectBuilder singleModule(Path root) {
        FixtureProjectBuilder builder = init(root);
        write(root.resolve("pom.xml"), pom("fixture-project", null));
        return builder;
    }

    /**
     * A 2-module Maven reactor rooted at {@code root}: {@code moduleA} and {@code moduleB},
     * where {@code moduleB} depends on {@code moduleA}. Git is initialized at the root.
     */
    public static FixtureProjectBuilder twoModuleReactor(Path root) {
        FixtureProjectBuilder builder = init(root);
        write(root.resolve("pom.xml"), reactorPom());
        write(root.resolve("moduleA/pom.xml"), pom("moduleA", null));
        write(root.resolve("moduleB/pom.xml"), pom("moduleB", "moduleA"));
        return builder;
    }

    /**
     * Adds {@code target/} and {@code .blastradius/} to {@code .gitignore} (default =
     * none — {@code commit()} does a blanket {@code git add .}). Only needed by tests
     * that run a real {@code mvn test} and/or write a dependency index against this
     * fixture *and then* make a further commit — without this, that build's own output
     * (surefire-reports, .class files, a persisted index, ...) gets committed as
     * spurious "changed files," incorrectly triggering the conservative non-source-
     * change fallback. Real adopting projects are expected to exclude both the same way
     * (quickstart.md).
     */
    public FixtureProjectBuilder ignoreTargetDirectory() {
        write(root.resolve(".gitignore"), "target/\n.blastradius/\n");
        return this;
    }

    private static FixtureProjectBuilder init(Path root) {
        try {
            Files.createDirectories(root);
            Git git = Git.init().setDirectory(root.toFile()).setInitialBranch("main").call();
            return new FixtureProjectBuilder(root, git);
        } catch (Exception e) {
            throw new IllegalStateException("failed to init fixture project at " + root, e);
        }
    }

    /** Write (or overwrite) a production class in the single-module project. */
    public FixtureProjectBuilder writeClass(String fqcn, String source) {
        return writeClassInModule(null, fqcn, source);
    }

    /** Write (or overwrite) a test class in the single-module project. */
    public FixtureProjectBuilder writeTest(String fqcn, String source) {
        return writeTestInModule(null, fqcn, source);
    }

    /** Write (or overwrite) a production class in {@code module} (null = single-module root). */
    public FixtureProjectBuilder writeClassInModule(String module, String fqcn, String source) {
        write(sourcePath(module, "main", fqcn), source);
        return this;
    }

    /** Write (or overwrite) a test class in {@code module} (null = single-module root). */
    public FixtureProjectBuilder writeTestInModule(String module, String fqcn, String source) {
        write(sourcePath(module, "test", fqcn), source);
        return this;
    }

    /** Delete a previously-written file (module-relative fqcn), e.g. to simulate removal. */
    public FixtureProjectBuilder deleteClassInModule(String module, String fqcn) {
        try {
            Files.deleteIfExists(sourcePath(module, "main", fqcn));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** Write an arbitrary non-source file (e.g. a resource) relative to the project root. */
    public FixtureProjectBuilder writeResource(String relativePath, String content) {
        write(root.resolve(relativePath), content);
        return this;
    }

    /**
     * Adds a {@code system}-scope dependency on a local jar (e.g. our own built agent
     * jar) to {@code module}'s pom.xml (null = single-module root). Used only by tests
     * that need a real jar on the fixture project's test classpath without installing
     * anything into the developer's real local Maven repository.
     */
    public FixtureProjectBuilder addSystemDependency(String module, Path jarPath) {
        Path pomPath = module == null ? root.resolve("pom.xml") : root.resolve(module).resolve("pom.xml");
        try {
            String pom = Files.readString(pomPath, StandardCharsets.UTF_8);
            String dependency = """
                        <dependency>
                            <groupId>fixture.system</groupId>
                            <artifactId>agent-jar</artifactId>
                            <version>0.0.1</version>
                            <scope>system</scope>
                            <systemPath>%s</systemPath>
                        </dependency>
                    """.formatted(jarPath.toAbsolutePath());
            pom = pom.replace("</dependencies>", dependency + "    </dependencies>");
            Files.writeString(pomPath, pom, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /**
     * Injects a raw {@code <plugin>...</plugin>} XML block into {@code module}'s pom.xml
     * (null = single-module root) — used by tests that need a real build plugin bound
     * (e.g. {@code blastradius-maven-plugin} itself), which must already be resolvable
     * from the local Maven repository (Maven plugins, unlike dependencies, have no
     * {@code system}-scope equivalent — install it first).
     */
    public FixtureProjectBuilder addBuildPlugin(String module, String pluginXml) {
        Path pomPath = module == null ? root.resolve("pom.xml") : root.resolve(module).resolve("pom.xml");
        try {
            String pom = Files.readString(pomPath, StandardCharsets.UTF_8);
            pom = pom.replace("</plugins>", pluginXml + "\n        </plugins>");
            Files.writeString(pomPath, pom, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** Stage everything and commit. Returns the full 40-character commit SHA. */
    public String commit(String message) {
        try {
            git.add().addFilepattern(".").call();
            var commit = git.commit()
                    .setMessage(message)
                    .setAuthor(AUTHOR)
                    .setCommitter(AUTHOR)
                    .call();
            return commit.getId().getName();
        } catch (Exception e) {
            throw new IllegalStateException("failed to commit fixture project change: " + message, e);
        }
    }

    public Path repoPath() {
        return root;
    }

    public Git git() {
        return git;
    }

    private Path sourcePath(String module, String mainOrTest, String fqcn) {
        Path base = module == null ? root : root.resolve(module);
        String relative = fqcn.replace('.', '/') + ".java";
        return base.resolve("src").resolve(mainOrTest).resolve("java").resolve(relative);
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String reactorPom() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>fixture</groupId>
                    <artifactId>fixture-reactor</artifactId>
                    <version>0.0.1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>moduleA</module>
                        <module>moduleB</module>
                    </modules>
                    <properties>
                        <maven.compiler.release>21</maven.compiler.release>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                </project>
                """;
    }

    private static String pom(String artifactId, String dependsOnModule) {
        String dependency = dependsOnModule == null ? "" : """
                    <dependency>
                        <groupId>fixture</groupId>
                        <artifactId>%s</artifactId>
                        <version>0.0.1</version>
                    </dependency>
                """.formatted(dependsOnModule);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>fixture</groupId>
                    <artifactId>%s</artifactId>
                    <version>0.0.1</version>
                    <packaging>jar</packaging>
                    <properties>
                        <maven.compiler.release>21</maven.compiler.release>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>
                    <dependencies>
                %s        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.2.5</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(artifactId, dependency);
    }
}
