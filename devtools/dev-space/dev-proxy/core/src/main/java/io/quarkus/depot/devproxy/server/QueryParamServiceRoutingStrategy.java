package io.quarkus.depot.devproxy.server;

import io.vertx.ext.web.RoutingContext;

/**
 * Match service name via query param
 */
public class QueryParamServiceRoutingStrategy implements ServiceRoutingStrategy {
    private final String name;

    public QueryParamServiceRoutingStrategy(String name) {
        this.name = name;
    }

    @Override
    public String match(RoutingContext ctx) {
        return ctx.queryParams().get(name);
    }
}
