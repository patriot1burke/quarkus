package io.quarkus.vertx.http.runtime.devmode;

import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

@Recorder
public class DevSpaceProxyRecorder {
    private static final Logger log = Logger.getLogger(DevSpaceProxyRecorder.class);

    static VirtualDevpaceProxyClient client;
    static DevspaceConfig config;
    static Vertx vertx;

    public void init(Supplier<Vertx> vertx, ShutdownContext shutdown, DevspaceConfig c, boolean delayConnect) {
        config = c;
        DevSpaceProxyRecorder.vertx = vertx.get();
        if (!delayConnect) {
            startSession();
            shutdown.addShutdownTask(() -> {
                closeSession();
            });
        }
    }

    public static void startSession() {
        client = new VirtualDevpaceProxyClient();
        HttpClientOptions options = new HttpClientOptions();
        options.setDefaultHost(config.host);
        options.setDefaultPort(config.port);
        if (config.ssl) {
            options.setSsl(true).setTrustAll(true);
        }
        client.proxyClient = vertx.createHttpClient(options);
        client.vertx = vertx;
        client.whoami = config.who;
        // todo add session support
        client.startGlobalSession();
    }

    public static void closeSession() {
        if (client != null)
            client.shutdown();
    }
}
