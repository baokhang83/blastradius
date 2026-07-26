package io.github.baokhang83.blastradius.plugin.mojo;

import java.util.Properties;

/** Claims the one tracking subprocess permitted for a reactor and commit in one Maven session. */
final class ReactorTrackingGate {

    private static final String PROPERTY_PREFIX = "blastradius.reactor-track.";

    private ReactorTrackingGate() {}

    static boolean claim(Properties sessionProperties, String reactorRoot, String commit) {
        String key = PROPERTY_PREFIX + Integer.toHexString((reactorRoot + "@" + commit).hashCode());
        synchronized (sessionProperties) {
            if (sessionProperties.containsKey(key)) {
                return false;
            }
            sessionProperties.setProperty(key, "claimed");
            return true;
        }
    }
}
