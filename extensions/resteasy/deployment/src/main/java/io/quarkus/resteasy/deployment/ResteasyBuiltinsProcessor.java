package io.quarkus.resteasy.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.resteasy.common.deployment.ResteasyJaxrsProviderBuildItem;
import io.quarkus.resteasy.runtime.NotFoundExceptionMapper;
import io.quarkus.resteasy.runtime.RolesFilterRegistrar;
import io.quarkus.runtime.LaunchMode;

public class ResteasyBuiltinsProcessor {
    /**
     * Install the JAX-RS security provider.
     */
    @BuildStep
    void setupFilter(BuildProducer<ResteasyJaxrsProviderBuildItem> providers) {
        providers.produce(new ResteasyJaxrsProviderBuildItem(RolesFilterRegistrar.class.getName()));
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    void setupExceptionMapper(BuildProducer<ResteasyJaxrsProviderBuildItem> providers, LaunchModeBuildItem launchMode) {
        if (launchMode.getLaunchMode().equals(LaunchMode.DEVELOPMENT)) {
            providers.produce(new ResteasyJaxrsProviderBuildItem(NotFoundExceptionMapper.class.getName()));
        }
    }

}
