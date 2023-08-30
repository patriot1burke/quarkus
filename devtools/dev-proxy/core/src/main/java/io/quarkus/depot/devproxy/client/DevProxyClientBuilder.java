package io.quarkus.depot.devproxy.client;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;

public class DevProxyClientBuilder {
    private DevProxyClient devProxyClient;
    private Vertx vertx;
    DevProxyClientBuilder(DevProxyClient devProxyClient) {
        this.devProxyClient = devProxyClient;
    }

    public DevProxyClientBuilder runtime(Vertx vertx) {
        this.vertx = vertx;
        return this;
    }

    public DevProxyClientBuilder whoami(String whoami) {
        devProxyClient.whoami = whoami;
        return this;
    }

    public DevProxyClientBuilder service(String name) {
        devProxyClient.service = name;
        return this;
    }

    public DevProxyClientBuilder numPollers(int num) {
        devProxyClient.numPollers = num;
        return this;
    }

    public DevProxyClientBuilder proxy(String host, int port, boolean ssl) {
        HttpClientOptions options = new HttpClientOptions();
        if (ssl) {
            options.setSsl(true).setTrustAll(true);
        }
        return proxy(host, port, options);
    }

    public DevProxyClientBuilder proxy(String host, int port, HttpClientOptions options) {
        devProxyClient.proxyHost = host;
        devProxyClient.proxyPort = port;
        options.setDefaultHost(host);
        options.setDefaultPort(port);
        devProxyClient.proxyClient = vertx.createHttpClient(options);
        return this;
    }

    public DevProxyClientBuilder service(String host, int port, boolean ssl) {
        HttpClientOptions options = new HttpClientOptions();
        if (ssl) {
            options.setSsl(true).setTrustAll(true);
        }
        return service(host, port, options);
    }
    public DevProxyClientBuilder service(String host, int port, HttpClientOptions options) {
        devProxyClient.serviceHost = host;
        devProxyClient.servicePort = port;
        options.setDefaultHost(host);
        options.setDefaultPort(port);
        devProxyClient.serviceClient = vertx.createHttpClient(options);
        return this;
    }

    public DevProxyClient build() {
        if (devProxyClient.serviceClient == null) {
            service("localhost", 8080, false);
        }
        return devProxyClient;
    }
}