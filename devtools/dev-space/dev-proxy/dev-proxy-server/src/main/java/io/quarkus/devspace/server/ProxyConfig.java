package io.quarkus.devspace.server;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "devspace")
public interface ProxyConfig {
    String name();

    String host();

    int port();
}
