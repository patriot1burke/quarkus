package io.quarkus;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.network.NetworkTemplate;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.virtual.VirtualTemplate;

@QuarkusTest
public class ExtensionTestCase {
    /**
     * Test the RuntimeXmlConfigService using old school sockets
     */
    @Test
    public void testRuntimeXmlConfigService() throws Exception {
        Assertions.assertTrue(NetworkTemplate.networkSetup);
        Assertions.assertFalse(VirtualTemplate.virtualCalled);
    }

}
