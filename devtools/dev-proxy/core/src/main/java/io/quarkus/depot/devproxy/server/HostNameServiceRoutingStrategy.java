package io.quarkus.depot.devproxy.server;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;

import io.vertx.ext.web.RoutingContext;

/**
 * Find service name via a regex within Host header. Must have a "service" group name.
 *
 * For example:
 *
 * (?<service>\w+)\.mycompany\.com
 */
public class HostNameServiceRoutingStrategy implements ServiceRoutingStrategy {
    protected static final Logger log = Logger.getLogger(HostNameServiceRoutingStrategy.class);
    private final Pattern pattern;

    public HostNameServiceRoutingStrategy(String regex) {
        regex = regex + "(:\\d+)?";
        pattern = Pattern.compile(regex);
    }

    @Override
    public String match(RoutingContext ctx) {
        String host = ctx.request().host();
        if (host == null) {
            log.debug("Host was null");
            return null;
        }
        log.debugv("Trying to match hostname {0} to pattern \"{1}\"", host, pattern.pattern());
        Matcher matcher = pattern.matcher(host);
        if (matcher.matches()) {
            return matcher.group("service");
        } else {
            return null;
        }
    }
}
