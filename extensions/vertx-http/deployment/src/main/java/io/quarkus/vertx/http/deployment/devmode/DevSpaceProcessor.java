package io.quarkus.vertx.http.deployment.devmode;

import org.jboss.logging.Logger;

import io.quarkus.builder.BuildException;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.vertx.http.deployment.RequireVirtualHttpBuildItem;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;

public class DevSpaceProcessor {
    private static final Logger log = Logger.getLogger(DevSpaceProcessor.class);

    @BuildStep(onlyIfNot = IsNormal.class) // This is required for testing so run it even if devservices.enabled=false
    public RequireVirtualHttpBuildItem requestVirtualHttp(HttpBuildTimeConfig config) throws BuildException {

        if (config.devspace.uri.isPresent()) {
            if (!config.devspace.whoami.isPresent()) {
                throw new BuildException("devspace.whomai must be set");
            }
            return RequireVirtualHttpBuildItem.ALWAYS_VIRTUAL;
        } else {
            return null;
        }
    }
}
