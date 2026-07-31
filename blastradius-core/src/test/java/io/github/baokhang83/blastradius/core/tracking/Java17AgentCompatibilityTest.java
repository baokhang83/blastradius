package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class Java17AgentCompatibilityTest {

    @Test
    void trackingAgentEntryPointIsLoadableByJava17() throws IOException {
        String resourceName = "/" + DependencyTrackingAgent.class.getName().replace('.', '/') + ".class";
        try (InputStream resource = DependencyTrackingAgent.class.getResourceAsStream(resourceName);
                DataInputStream classFile = new DataInputStream(resource)) {
            assertEquals(0xCAFEBABE, classFile.readInt());
            classFile.readUnsignedShort(); // minor version
            assertEquals(61, classFile.readUnsignedShort()); // Java 17
        }
    }
}
