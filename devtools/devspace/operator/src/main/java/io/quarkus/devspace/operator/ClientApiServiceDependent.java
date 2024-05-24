package io.quarkus.devspace.operator;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

@KubernetesDependent(resourceDiscriminator = ClientApiServiceDependent.ClientApiDiscriminator.class)
public class ClientApiServiceDependent extends CRUDKubernetesDependentResource<Service, DevspaceProxy> {
    public static class ClientApiDiscriminator implements ResourceDiscriminator<Service, DevspaceProxy> {
        @Override
        public Optional<Service> distinguish(Class<Service> resource, DevspaceProxy primary, Context<DevspaceProxy> context) {
            InformerEventSource<Service, DevspaceProxy> ies =
                    (InformerEventSource<Service, DevspaceProxy>) context
                            .eventSourceRetriever().getResourceEventSourceFor(Service.class);

            return ies.get(new ResourceID("proxy-client-" + primary.getMetadata().getName(),
                    primary.getMetadata().getNamespace()));        }


    }
    public ClientApiServiceDependent() {
        super(Service.class);
    }

    @Inject
    KubernetesClient client;


    @Override
    protected Service desired(DevspaceProxy primary, Context<DevspaceProxy> context) {
        String serviceName = primary.getMetadata().getName();
        String name = "proxy-client-" + serviceName;
        Service service = client.services().withName(serviceName).get();
        return new ServiceBuilder()
                .withMetadata(DevspaceProxyReconciler.createMetadata(primary, name))
                .withNewSpec()
                .addNewPort()
                .withName("http")
                .withPort(80)
                .withProtocol("TCP")
                .withTargetPort(new IntOrString(8081))
                .endPort()
                .withSelector(Map.of("run", "proxy-" + serviceName))
                .withType("NodePort")
                .endSpec().build();
    }
}
