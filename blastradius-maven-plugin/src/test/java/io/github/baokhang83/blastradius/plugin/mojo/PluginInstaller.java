package io.github.baokhang83.blastradius.plugin.mojo;

import java.io.IOException;

final class PluginInstaller {

    private boolean installed;

    synchronized void installOnce(InstallAction action) throws IOException, InterruptedException {
        if (installed) {
            return;
        }
        action.run();
        installed = true;
    }

    @FunctionalInterface
    interface InstallAction {

        void run() throws IOException, InterruptedException;
    }
}
