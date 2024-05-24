package io.quarkus.devspace.operator;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceFluent;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ContextInitializer;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE, name = "devspaceproxy", dependents = {
        @Dependent(type = ProxyDeploymentDependent.class),
        @Dependent(type = ClientApiServiceDependent.class, useEventSourceWithName = "ServiceEventSource"),
        @Dependent(type = OriginServiceDependent.class, useEventSourceWithName = "ServiceEventSource")
})
@CSVMetadata(displayName = "Devspace operator", description = "Setup of Devspace Proxies")
public class DevspaceProxyReconciler implements Reconciler<DevspaceProxy>, ContextInitializer<DevspaceProxy>, Cleaner<DevspaceProxy>, EventSourceInitializer<DevspaceProxy> {
    protected static final Logger log = Logger.getLogger(DevspaceProxyReconciler.class);

    @Inject
    KubernetesClient client;

    @Override
    public void initContext(DevspaceProxy primary, Context<DevspaceProxy> context) {
    }

    @Override
    public UpdateControl<DevspaceProxy> reconcile(DevspaceProxy devspaceProxy, Context<DevspaceProxy> context) {
        log.info("reconcile");
        ServiceResource<Service> serviceResource = client.services().withName(devspaceProxy.getMetadata().getName());
        Service service = serviceResource.get();
        Map<String, String> oldSelectors = new HashMap<>();
        oldSelectors.putAll(service.getSpec().getSelector());
        final var name = devspaceProxy.getMetadata().getName();
        String proxyDeploymentName = "proxy-" + service.getMetadata().getName();

        UnaryOperator<Service> edit = (s) -> {
            ServiceBuilder builder = new ServiceBuilder(s);
            ServiceFluent<ServiceBuilder>.SpecNested<ServiceBuilder> spec = builder.editSpec();
            spec.getSelector().clear();
            spec.getSelector().put("run", proxyDeploymentName);
            return spec.endSpec().build();

        };
        serviceResource.edit(edit);

        // retrieve the workflow reconciliation result and re-schedule if we have dependents that are not yet ready
        log.info("waiting on dependents....");
        return context.managedDependentResourceContext().getWorkflowReconcileResult()
                .map(wrs -> {
                    log.info("******** got dependents....");
                    if (wrs.allDependentResourcesReady()) {
                        DevspaceProxyStatus status = new DevspaceProxyStatus();
                        status.setOldSelectors(oldSelectors);
                        status.setInitialized(true);
                        devspaceProxy.setStatus(status);
                        return UpdateControl.updateStatus(devspaceProxy);
                    } else {
                        final var duration = Duration.ofSeconds(1);
                        log.infov("App {0} is not ready yet, rescheduling reconciliation after {1}s", name, duration.toSeconds());
                        return UpdateControl.<DevspaceProxy> noUpdate().rescheduleAfter(duration);
                    }
                }).orElseThrow();
    }

    @Override
    public DeleteControl cleanup(DevspaceProxy devspaceProxy, Context<DevspaceProxy> context) {
        log.info("cleanup");
        if (devspaceProxy.getStatus() == null || devspaceProxy.getStatus().getOldSelectors() == null) {
            return DeleteControl.defaultDelete();
        }
        ServiceResource<Service> serviceResource = client.services().withName(devspaceProxy.getMetadata().getName());
        UnaryOperator<Service> edit = (s) -> {
            return new ServiceBuilder(s)
                    .editSpec()
                    .withSelector(devspaceProxy.getStatus().getOldSelectors())
                    .endSpec().build();

        };
        serviceResource.edit(edit);
        return DeleteControl.defaultDelete();
    }

    static ObjectMeta createMetadata(DevspaceProxy resource, String name) {
        final var metadata = resource.getMetadata();
        return new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(metadata.getNamespace())
                .withLabels(Map.of("app.kubernetes.io/name", name))
                .build();
    }


    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<DevspaceProxy> context) {
        InformerEventSource<Service, DevspaceProxy> ies =
                new InformerEventSource<>(InformerConfiguration.from(Service.class, context)
                        .build(), context);

        return Map.of("ServiceEventSource", ies);
    }
}