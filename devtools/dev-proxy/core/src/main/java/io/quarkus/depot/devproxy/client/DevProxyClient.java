package io.quarkus.depot.devproxy.client;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.concurrent.atomic.AtomicBoolean;

public class DevProxyClient {
    protected WebClient client;
    protected boolean proxySsl;
    protected String proxyHost;
    protected int proxyPort;
    protected String serviceHost = "localhost";
    protected int servicePort = 8080;
    protected boolean serviceSsl = false;
    protected String whoami;
    protected String sessionId;
    protected int numPollers = 1;
    protected volatile boolean running = true;

    public DevProxyClient runtime(Vertx vertx, WebClientOptions options) {
        client = WebClient.create(vertx, options);
        return this;
    }
    public DevProxyClient runtime(Vertx vertx) {
        client = WebClient.create(vertx);
        return this;
    }

    public DevProxyClient runtime(WebClient client) {
        this.client = client;
        return this;
    }

    public DevProxyClient whoami(String whoami) {
        this.whoami = whoami;
        return this;
    }
    public DevProxyClient numPollers(int num) {
        this.numPollers = num;
        return this;
    }
    public DevProxyClient proxyConnection(String host, int port, boolean ssl) {
        this.proxyHost = host;
        this.proxyPort = port;
        this.proxySsl = ssl;
        return this;
    }
    public DevProxyClient serviceConnection(String host, int port, boolean ssl) {
        this.serviceHost = host;
        this.servicePort = port;
        this.serviceSsl = ssl;
        return this;
    }

    public void startSession(String sessionId) {

    }
    public void startGlobalSession() {

    }

    protected void poll() {
    }

    public void shutdown() {
        running = false;
        client.close();
    }


}
