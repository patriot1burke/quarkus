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

    public static VirtualDevpaceProxyClient client;
    public static DevspaceConfig config;

    public void init(Supplier<Vertx> vertx, DevspaceConfig c) {
        log.info("Initializing devspace");
        URI uri = null;
        config = c;
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
        client.proxyClient = vertx.get().createHttpClient(options);
        client.vertx = vertx.get();
        client.whoami = c.whoami.get();
        if (!config.delayConnect) {
            startSession();
        } else {
            log.info(" --- delay connect ---");
        }
    }

    public static void startSession() {
        if (config.session.isPresent()) {
            client.startSession(config.session.get());
        } else {
            client.startGlobalSession();
        }
    }
}
