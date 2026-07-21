package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PluginInstallerTest {

    @Test
    void installsOnlyOnceAfterASuccessfulInstall() throws Exception {
        PluginInstaller installer = new PluginInstaller();
        AtomicInteger installations = new AtomicInteger();

        installer.installOnce(installations::incrementAndGet);
        installer.installOnce(installations::incrementAndGet);

        assertEquals(1, installations.get());
    }

    @Test
    void retriesWhenTheFirstInstallFails() throws Exception {
        PluginInstaller installer = new PluginInstaller();
        AtomicInteger installations = new AtomicInteger();

        assertThrows(IOException.class, () -> installer.installOnce(() -> {
            installations.incrementAndGet();
            throw new IOException("temporary failure");
        }));
        installer.installOnce(installations::incrementAndGet);

        assertEquals(2, installations.get());
    }
}
