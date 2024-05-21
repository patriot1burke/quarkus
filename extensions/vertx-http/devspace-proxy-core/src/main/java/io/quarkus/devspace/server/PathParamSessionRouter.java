package io.quarkus.devspace.server;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathParamSessionRouter implements RequestSessionRouter {
    private final Pattern pattern;

    public PathParamSessionRouter(String pathExpression) {
        String regex = pathExpression.replace("<service>", "(?<service>[^/]+)");
        pattern = Pattern.compile(regex + ".*");
    }

    @Override
    public String match(RoutingContext ctx) {
        String path = ctx.normalizedPath();
        return match(path);
    }

    public String match(String path) {
        if (path == null)
            return null;
        Matcher matcher = pattern.matcher(path);
        if (matcher.matches()) {
            return matcher.group("service");
        } else {
            return null;
        }
    }
}
