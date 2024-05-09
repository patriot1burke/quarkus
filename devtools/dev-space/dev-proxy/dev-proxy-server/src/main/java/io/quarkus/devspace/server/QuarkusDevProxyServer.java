package io.quarkus.devspace.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

@ApplicationScoped
public class QuarkusDevProxyServer extends DevProxyServer {

    @Inject
    protected ProxyConfig config;

    public void start(@Observes StartupEvent start, Vertx vertx, Router router) {
        this.vertx = vertx;
        this.router = router;
        init(new ServiceConfig(config.name(), config.host(), config.port()));
    }
}
