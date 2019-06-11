package io.quarkus.resteasy.server.common.deployment;

import org.jboss.resteasy.spi.ResteasyDeployment;

import io.quarkus.builder.item.SimpleBuildItem;

public final class ResteasyDeploymentBuildItem extends SimpleBuildItem {
    private ResteasyDeployment deployment;

    public ResteasyDeploymentBuildItem(ResteasyDeployment deployment) {
        this.deployment = deployment;
    }

    public ResteasyDeployment getDeployment() {
        return deployment;
    }
}
