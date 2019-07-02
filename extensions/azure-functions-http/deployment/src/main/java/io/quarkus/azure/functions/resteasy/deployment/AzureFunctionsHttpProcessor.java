package io.quarkus.azure.functions.resteasy.deployment;

import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.netty.http.deployment.RequireVirtualHttpBuildItem;
import io.quarkus.netty.http.deployment.SuppressHttpSocketBuildItem;
import io.quarkus.runtime.LaunchMode;

public class AzureFunctionsHttpProcessor {
    private static final Logger log = Logger.getLogger(AzureFunctionsHttpProcessor.class);

    @BuildStep
    public SuppressHttpSocketBuildItem suppressHttpSocket(LaunchModeBuildItem launchMode) {
        return launchMode.getLaunchMode() == LaunchMode.NORMAL ? SuppressHttpSocketBuildItem.MARKER : null;
    }

    @BuildStep
    public RequireVirtualHttpBuildItem requestVirtualHttp() {
        return RequireVirtualHttpBuildItem.MARKER;
    }

}
