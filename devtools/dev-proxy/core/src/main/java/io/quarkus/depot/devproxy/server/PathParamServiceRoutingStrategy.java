package io.quarkus.depot.devproxy.server;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;

import io.vertx.ext.web.RoutingContext;

/**
 * Find service name via a regex of the path of the service
 *
 * For example:
 * /(?<service>[^/]+)
 *
 * ".*" will be added to the end of every expression for convenience.
 *
 *
 *
 */
public class PathParamServiceRoutingStrategy implements ServiceRoutingStrategy {
    protected static final Logger log = Logger.getLogger(PathParamServiceRoutingStrategy.class);
    private final Pattern pattern;

    public PathParamServiceRoutingStrategy() {
        this("/(?<service>[^/]+)");
    }

    public PathParamServiceRoutingStrategy(String regex) {
        pattern = Pattern.compile(regex + ".*");
    }

    @Override
    public String match(RoutingContext ctx) {
        String path = ctx.normalizedPath();
        log.debugv("Trying to match path {0} to pattern \"{1}\"", path, pattern.pattern());
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
