package io.quarkus.network;

import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

public class NetworkProcessor {
    private static final Logger log = Logger.getLogger(NetworkProcessor.class.getName());

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void setupNetwork(Optional<ExcludeNetworkExtensionBuildItem> virtualSetup, NetworkTemplate template) {
        log.error("---- NetworkProcessor: will exclude " + virtualSetup.isPresent());
        if (!virtualSetup.isPresent()) {
            template.setup();
        }
    }

}
