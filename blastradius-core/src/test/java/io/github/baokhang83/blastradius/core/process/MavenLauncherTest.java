package io.github.baokhang83.blastradius.core.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MavenLauncherTest {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    @Test
    void launcherNameIsTheCmdScriptOnWindowsAndBareMvnElsewhere() {
        // The whole point of this class: Windows has no `mvn` executable — Maven ships
        // `mvn.cmd` — and Java's ProcessBuilder only appends `.exe` when resolving a bare
        // name against PATH, so a literal "mvn" fails with CreateProcess error=2. Every
        // other OS uses the bare `mvn` shell script.
        if (IS_WINDOWS) {
            assertEquals("mvn.cmd", MavenLauncher.launcherName());
        } else {
            assertEquals("mvn", MavenLauncher.launcherName());
        }
    }

    @Test
    void resolveFallsBackToTheBareLauncherNameWhenNoMavenHomePointsAtARealInstall() {
        // With no valid MAVEN_HOME/M2_HOME on this machine, resolve() must degrade to the
        // bare platform launcher name (found via PATH) rather than an absolute path — and
        // that name must still be the OS-correct one, never a naked "mvn" on Windows.
        String resolved = MavenLauncher.resolve();
        assertTrue(resolved.endsWith(MavenLauncher.launcherName()),
                "resolve() must end with the OS launcher name, got: " + resolved);
    }

    @Test
    void resolveNeverReturnsABareMvnTokenThatWouldFailToLaunchOnWindows() {
        // Guards the regression this class exists to prevent: on Windows the resolved
        // command must never be the literal "mvn" (the token that throws CreateProcess
        // error=2). It's either an absolute path into a Maven install or "mvn.cmd".
        String resolved = MavenLauncher.resolve();
        if (IS_WINDOWS) {
            assertTrue(!resolved.equals("mvn"),
                    "on Windows the launcher must be mvn.cmd or an absolute path, never bare mvn");
        }
    }
}
