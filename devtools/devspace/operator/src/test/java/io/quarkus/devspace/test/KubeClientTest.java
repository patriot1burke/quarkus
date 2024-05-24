package io.quarkus.devspace.test;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceFluent;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.UnaryOperator;

public class KubeClientTest {

    //@Test
    public void kubeClientTest() throws Exception {
        KubernetesClient client = new KubernetesClientBuilder().build();
        for (Service service : client.services().list().getItems()) {
            System.out.println("service: " + service.getMetadata().getName());
            System.out.println("    selector:");
            service.getSpec().getSelector().entrySet().forEach((entry) -> System.out.println("        " + entry.getKey() + ": " + entry.getValue()));
        }

        ServiceResource<Service> serviceResource = client.services().withName("test-rest-service");
        Service service = serviceResource.get();
        List<ServicePort> ports = service.getSpec().getPorts();
        if (ports.size() != 1) {
            throw new RuntimeException("Num ports not 1");
        }
        String targetPort = ports.get(0).getTargetPort().getStrVal();

        String text;
        text = readResource("origin-template.yml");
        String serviceName = service.getMetadata().getName();
        text = text.replace("SERVICE_NAME", "origin-" + serviceName);
        InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        Service origin = client.services().load(is).item();
        origin.getSpec().getSelector().clear();
        service.getSpec().getSelector().entrySet().forEach((entry) -> origin.getSpec().getSelector().put(entry.getKey(),entry.getValue()));
        client.resource(origin).create();



        text = readResource("deployment-template.yml");
        String proxyDeploymentName = "proxy-" + serviceName;
        text = text.replace("PROXY_DEPLOYMENT_NAME", proxyDeploymentName);
        text = text.replace("SERVICE_NAME_VALUE", serviceName);
        text = text.replace("SERVICE_HOST_VALUE", "origin-" + serviceName);
        text = text.replace("SERVICE_PORT_VALUE", "80"); // todo
        text = text.replace("SERVICE_SSL_VALUE", "false"); // todo
        text = text.replace("QUARKUS_VERSION", "999-SNAPSHOT");
        is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        Deployment deployment = client.apps().deployments().load(is).item();
        client.resource(deployment).create();

        text = readResource("client-template.yml");
        text = text.replace("SERVICE_NAME", serviceName);
        text = text.replace("SERVICE_SELECTOR", proxyDeploymentName);
        is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        Service clientApi = client.services().load(is).item();
        client.resource(clientApi).create();


         UnaryOperator<Service> edit = (s) -> {
            ServiceBuilder builder = new ServiceBuilder(s);
            ServiceFluent<ServiceBuilder>.SpecNested<ServiceBuilder> spec = builder.editSpec();
            spec.getSelector().clear();
            spec.getSelector().put("run", proxyDeploymentName);
            return spec.endSpec().build();

        };
        serviceResource.edit(edit);
        throw new RuntimeException("EXIT"); // todo
    }

    private String readResource(String file) throws IOException {
        String text = null;
        try (InputStream in = getClass().getResourceAsStream("/" + file)) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return text;
    }
}
