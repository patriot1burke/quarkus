package io.quarkus.vertx.http.runtime.devmode;

import java.util.List;

/**
 * Populated from quarkus.http.devspace
 */
public class DevspaceConfig {
    public String who;
    public String host;
    public int port;
    public boolean ssl;
    public List<String> headers;
    public List<String> paths;
    public List<String> queries;
    public String session;
    public boolean delayConnect;
}
