package io.quarkus.devspace.server;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

/**
 *
 */
public interface RequestSessionRouter {
    String match(RoutingContext ctx);
}
