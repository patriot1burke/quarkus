package io.quarkus.devspace.server;

import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ConfigProperties(prefix = "service")
@Dependent
public class ProxyConfig {
    public String name;

    public String host;

    public int port;

    @ConfigProperty(defaultValue = "false")
    public boolean ssl;
}
