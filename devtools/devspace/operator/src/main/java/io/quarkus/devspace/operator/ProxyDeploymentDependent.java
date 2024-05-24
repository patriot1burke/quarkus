package io.quarkus.devspace.operator;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;

public class ProxyDeploymentDependent extends CRUDKubernetesDependentResource<Deployment, DevspaceProxy> {
    protected static final Logger log = Logger.getLogger(ProxyDeploymentDependent.class);

    public ProxyDeploymentDependent() {
        super(Deployment.class);
    }

    //@ConfigProperty(name="quarkus.application.version")
    String quarkusVersion = "999-SNAPSHOT";


    @Override
    protected Deployment desired(DevspaceProxy primary, Context<DevspaceProxy> context) {
        String serviceName = primary.getMetadata().getName();
        String name = "proxy-" + serviceName;

        return new DeploymentBuilder()
                .withMetadata(DevspaceProxyReconciler.createMetadata(primary, name))
                .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                .withMatchLabels(Map.of("run", name))
                .endSelector()
                .withNewTemplate().withNewMetadata().addToLabels(Map.of("run", name)).endMetadata()
                .withNewSpec()
                .addNewContainer()
                .addNewEnv().withName("SERVICE_NAME").withValue(serviceName).endEnv()
                .addNewEnv().withName("SERVICE_HOST").withValue("origin-" + serviceName).endEnv()
                .addNewEnv().withName("SERVICE_PORT").withValue("80").endEnv()
                .addNewEnv().withName("SERVICE_SSL").withValue("false").endEnv()
                .addNewEnv().withName("CLIENT_API_PORT").withValue("8081").endEnv()
                .withImage("docker.io/io.quarkus/quarkus-devspace-proxy:" + quarkusVersion)
                .withImagePullPolicy("IfNotPresent")
                .withName(name)
                .addNewPort().withName("proxy-http").withContainerPort(8080).withProtocol("TCP").endPort()
                .addNewPort().withName("client-http").withContainerPort(8081).withProtocol("TCP").endPort()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }
}
