package io.quarkus.azure.functions.resteasy.runtime;

import org.jboss.resteasy.spi.ResteasyDeployment;

import io.quarkus.runtime.annotations.Template;

@Template
public class AzureFunctionsResteasyTemplate {
    public static ResteasyDeployment deployment;

    public void start(ResteasyDeployment dep) {
        deployment = dep;
        deployment.start();
    }
}
