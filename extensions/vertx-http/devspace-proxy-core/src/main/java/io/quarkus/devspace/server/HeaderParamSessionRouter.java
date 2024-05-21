package io.quarkus.devspace.server;

import io.vertx.ext.web.RoutingContext;

public class HeaderParamSessionRouter implements RequestSessionRouter {
    private final String name;

    public HeaderParamSessionRouter(String name) {
        this.name = name;
    }

    @Override
    public String match(RoutingContext ctx) {
        return ctx.request().headers().get(name);
    }
}
