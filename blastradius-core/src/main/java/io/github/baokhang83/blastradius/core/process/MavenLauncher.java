package io.github.baokhang83.blastradius.core.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves the name of the Maven launcher to hand to {@link ProcessBuilder}, per operating
 * system. On Windows there is no {@code mvn} executable — Maven ships {@code mvn.cmd} (and
 * {@code mvn.bat} on older distributions) — and Java's process launcher only appends
 * {@code .exe} when searching {@code PATH}, so a literal {@code "mvn"} fails with
 * {@code CreateProcess error=2, The system cannot find the file specified}. Naming the
 * {@code .cmd} explicitly (or an absolute path to it) is what makes the subprocess start.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code MAVEN_HOME}/{@code M2_HOME}{@code /bin/<launcher>} if that env var points at a
 *       real installation — an absolute path never depends on {@code PATH} at all;</li>
 *   <li>otherwise the bare platform launcher name ({@code mvn.cmd} on Windows, {@code mvn}
 *       elsewhere), resolved against {@code PATH} by the OS.</li>
 * </ol>
 */
public final class MavenLauncher {

    private MavenLauncher() {}

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** The bare launcher name for this OS: {@code mvn.cmd} on Windows, {@code mvn} otherwise. */
    public static String launcherName() {
        return IS_WINDOWS ? "mvn.cmd" : "mvn";
    }

    /**
     * The command token to pass as {@code argv[0]}: an absolute path to the launcher inside a
     * {@code MAVEN_HOME}/{@code M2_HOME} installation when one is set and valid, else the bare
     * platform launcher name (found via {@code PATH}).
     */
    public static String resolve() {
        for (String var : new String[] {"MAVEN_HOME", "M2_HOME"}) {
            String home = System.getenv(var);
            if (home == null || home.isBlank()) {
                continue;
            }
            Path candidate = Path.of(home, "bin", launcherName());
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return launcherName();
    }
}
