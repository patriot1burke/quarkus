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

import java.util.Optional;

@KubernetesDependent(resourceDiscriminator = OriginServiceDependent.OriginDescriminator.class)
public class OriginServiceDependent extends CRUDKubernetesDependentResource<Service, Devspace> {
    public static class OriginDescriminator implements ResourceDiscriminator<Service, Devspace> {
        @Override
        public Optional<Service> distinguish(Class<Service> resource, Devspace primary, Context<Devspace> context) {
            InformerEventSource<Service, Devspace> ies =
                    (InformerEventSource<Service, Devspace>) context
                            .eventSourceRetriever().getResourceEventSourceFor(Service.class);

            return ies.get(new ResourceID(origin(primary),
                    primary.getMetadata().getNamespace()));        }


    }

    public static String origin(Devspace primary) {
        return primary.getMetadata().getName() + "-origin";
    }

    public OriginServiceDependent() {
        super(Service.class);
    }

    @Inject
    KubernetesClient client;

    @Override
    protected Service desired(Devspace primary, Context<Devspace> context) {
        String serviceName = primary.getMetadata().getName();
        String name = origin(primary);
        Service service = client.services().withName(serviceName).get();
        return new ServiceBuilder()
                .withMetadata(DevspaceReconciler.createMetadata(primary, name))
                .withNewSpec()
                .addNewPort()
                .withName("http")
                .withPort(80)
                .withProtocol("TCP")
                .withTargetPort(new IntOrString(8080))
                .endPort()
                .withSelector(service.getSpec().getSelector())
                .withType("ClusterIP")
                .endSpec().build();
    }
}
