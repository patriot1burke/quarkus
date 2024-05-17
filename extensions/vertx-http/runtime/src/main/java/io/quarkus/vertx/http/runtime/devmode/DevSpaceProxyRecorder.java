package io.quarkus.vertx.http.runtime.devmode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

@Recorder
public class DevSpaceProxyRecorder {
    private static final Logger log = Logger.getLogger(DevSpaceProxyRecorder.class);

    static VirtualDevpaceProxyClient client;
    static DevspaceConfig config;
    static Vertx vertx;

    public void init(Supplier<Vertx> vertx, DevspaceConfig c) {
        config = c;
        DevSpaceProxyRecorder.vertx = vertx.get();
        if (!config.delayConnect) {
            startSession();
        }
    }

    public static void startSession() {
        URI uri = null;
        try {
            uri = new URI(config.uri.get());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        client = new VirtualDevpaceProxyClient();
        HttpClientOptions options = new HttpClientOptions();
        String host = uri.getHost();
        int port = uri.getPort();
        boolean ssl = uri.getScheme().equalsIgnoreCase("https");
        if (ssl) {
            options.setSsl(true).setTrustAll(true);
        }
        options.setDefaultHost(host);
        options.setDefaultPort(port);
        client.proxyClient = vertx.createHttpClient(options);
        client.vertx = vertx;
        client.whoami = config.whoami.get();
        if (config.session.isPresent()) {
            client.startSession(config.session.get());
        } else {
            client.startGlobalSession();
        }
    }

    public static void closeSession() {
        if (client != null)
            client.shutdown();
    }
}
