package io.quarkus.azure.functions.runtime;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AzureFunctionsRecorder {
    public void setupIntegration(BeanContainer container) {
        QuarkusFunctionFactory.CONTAINER = container;
    }
}
