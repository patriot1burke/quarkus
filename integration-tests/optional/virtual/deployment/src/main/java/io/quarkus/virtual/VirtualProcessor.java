package io.quarkus.virtual;

import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.network.ExcludeNetworkExtensionBuildItem;
import io.quarkus.runtime.LaunchMode;

public class VirtualProcessor {
    private static final Logger log = Logger.getLogger(VirtualProcessor.class.getName());

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    ExcludeNetworkExtensionBuildItem tag(LaunchModeBuildItem launchMode, VirtualTemplate template) {
        log.error("*********** VirtualProcessor.tag(): " + launchMode.getLaunchMode());
        if (launchMode.getLaunchMode() != LaunchMode.NORMAL) {
            return null;
        }
        log.error("*********** tagged! *******");
        template.setupVirtual();
        return new ExcludeNetworkExtensionBuildItem();
    }
}
