package io.quarkus.aws.lambda.deployment;

import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.deployment.RequireVirtualHttpBuildItem;

public class AwsLambdaHttpProcessor {
    private static final Logger log = Logger.getLogger(AwsLambdaHttpProcessor.class);

    @BuildStep
    public RequireVirtualHttpBuildItem requestVirtualHttp(LaunchModeBuildItem launchMode) {
        return launchMode.getLaunchMode() == LaunchMode.NORMAL ? RequireVirtualHttpBuildItem.MARKER : null;
    }
}
