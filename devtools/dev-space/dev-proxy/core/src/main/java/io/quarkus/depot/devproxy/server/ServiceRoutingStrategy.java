package io.quarkus.depot.devproxy.server;

import io.vertx.ext.web.RoutingContext;

public interface ServiceRoutingStrategy {
    String match(RoutingContext ctx);
}
