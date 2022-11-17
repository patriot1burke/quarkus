package io.quarkus.azure.functions.resteasy.deployment;

import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.LegacyJarRequiredBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.deployment.RequireVirtualHttpBuildItem;

public class AzureFunctionsHttpProcessor {
    private static final Logger log = Logger.getLogger(AzureFunctionsHttpProcessor.class);

    @BuildStep
    public LegacyJarRequiredBuildItem forceLegacy(PackageConfig config) {
        // Azure Functions need a legacy jar and no runner
        config.addRunnerSuffix = false;
        return new LegacyJarRequiredBuildItem();
    }

    @BuildStep
    public RequireVirtualHttpBuildItem requestVirtualHttp(LaunchModeBuildItem launchMode) {
        return launchMode.getLaunchMode() == LaunchMode.NORMAL ? RequireVirtualHttpBuildItem.MARKER : null;
    }
}
