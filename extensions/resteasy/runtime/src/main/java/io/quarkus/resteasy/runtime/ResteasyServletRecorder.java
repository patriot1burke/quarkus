package io.quarkus.resteasy.runtime;

import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;

import io.quarkus.resteasy.common.runtime.ResteasyRegistrationRecorder;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ResteasyServletRecorder {

    public void initialize() {
    }

    public static QuarkusResteasyDeployment deployment;

    public void initialize(QuarkusResteasyDeployment dep) {
        deployment = dep;
        if (ResteasyFilter.QUARKUS_INSTANCE != null) {
            ResteasyFilter.QUARKUS_INSTANCE.preDeploy(deployment);
        } else if (ResteasyFilter.QUARKUS_INSTANCE != null) {
            ResteasyServlet.QUARKUS_INSTANCE.preDeploy(deployment);
        }
        deployment.initialize();
        ResteasyRegistrationRecorder.providerFactory = (ResteasyProviderFactoryImpl) deployment.getProviderFactory();
    }

    public void startDeployment(String path) {
        deployment.start();

        if (ResteasyFilter.QUARKUS_INSTANCE != null) {
            ResteasyFilter.QUARKUS_INSTANCE.postDeploy(deployment, path);
        } else if (ResteasyServlet.QUARKUS_INSTANCE != null) {
            ResteasyServlet.QUARKUS_INSTANCE.postDeploy(deployment, path);
        }
        ResteasyRegistrationRecorder.providerFactory = null;
    }
}
