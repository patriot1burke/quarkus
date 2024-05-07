package io.quarkus.depot.devproxy.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

@ApplicationScoped
public class QuarkusDevProxyServer extends DevProxyServer {

    public void start(@Observes StartupEvent start, Vertx vertx, Router router) {
        this.vertx = vertx;
        this.router = router;
        init();
    }
}
