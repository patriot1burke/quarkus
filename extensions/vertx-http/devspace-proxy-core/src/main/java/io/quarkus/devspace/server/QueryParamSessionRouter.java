package io.quarkus.devspace.server;

import io.vertx.ext.web.RoutingContext;

public class QueryParamSessionRouter implements RequestSessionRouter {
    private final String name;

    public QueryParamSessionRouter(String name) {
        this.name = name;
    }

    @Override
    public String match(RoutingContext ctx) {
        return ctx.queryParams().get(name);
    }
}
