package io.quarkus.vertx.http;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

import java.util.concurrent.TimeUnit;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devspace.ProxyUtils;
import io.quarkus.devspace.server.DevProxyServer;
import io.quarkus.devspace.server.ServiceConfig;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.vertx.http.runtime.devmode.DevSpaceProxyRecorder;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.spi.VertxThreadFactory;
import io.vertx.ext.web.Router;

public class DevSpaceSessionProxyTest {

    public static final int SERVICE_PORT = 9091;
    public static final int PROXY_PORT = 9092;

    private static final String APP_PROPS = "" +
            "quarkus.http.devspace=http://localhost:9092?who=bill&session=john&path=/users/[]&query=user\n"
            + "quarkus.http.devspace-delay-connect=true\n";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource(new StringAsset(APP_PROPS), "application.properties")
                    .addClasses(DevSpaceSessionProxyTest.RouteProducer.class));

    public static DevProxyServer proxyServer;
    public static HttpServer proxy;

    static HttpServer myService;

    @Singleton
    public static class RouteProducer {
        void observeRouter(@Observes Router router) {
            router.route().handler(
                    request -> {
                        System.out.println("************ CALLED LOCAL SERVER **************");
                        request.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end("local");
                    });
        }

    }

    static Vertx vertx;

    @BeforeAll
    public static void before() {
        vertx = new VertxBuilder()
                .threadFactory(new VertxThreadFactory() {
                    public VertxThread newVertxThread(Runnable target, String name, boolean worker, long maxExecTime,
                            TimeUnit maxExecTimeUnit) {
                        return new VertxThread(target, "TEST-VERTX." + name, worker, maxExecTime, maxExecTimeUnit);
                    }
                }).init().vertx();
        myService = vertx.createHttpServer();
        ProxyUtils.await(1000, myService.requestHandler(request -> {
            request.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end("my-service");
        }).listen(SERVICE_PORT));

        proxy = vertx.createHttpServer();
        proxyServer = new DevProxyServer();
        Router proxyRouter = Router.router(vertx);
        ServiceConfig config = new ServiceConfig("my-service", "localhost", SERVICE_PORT);
        proxyServer.init(vertx, proxyRouter, config);
        ProxyUtils.await(1000, proxy.requestHandler(proxyRouter).listen(PROXY_PORT));
    }

    @AfterAll
    public static void after() {
        System.out.println(" -------    CLEANUP TEST ------");
        if (vertx != null) {
            ProxyUtils.await(1000, myService.close());
            System.out.println(" -------    Cleaned up my-service ------");
            ProxyUtils.await(1000, proxy.close());
            System.out.println(" -------    Cleaned up proxy ------");
            ProxyUtils.await(1000, vertx.close());
            System.out.println(" -------    Cleaned up test vertx ------");
        }
    }

    @Test
    public void testSession() throws Exception {

        try {
            DevSpaceProxyRecorder.startSession();
            System.out.println("-------------------- Query GET REQUEST --------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/yo?user=john")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("-------------------- Path GET REQUEST --------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/users/john/stuff")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("-------------------- Header GET REQUEST --------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .header(DevProxyServer.SESSION_HEADER, "john")
                    .get("/stuff")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("-------------------- Cookie GET REQUEST --------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .cookie(DevProxyServer.SESSION_HEADER, "john")
                    .get("/stuff")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("------------------ No session ---------------------");
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/yo")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("my-service"));
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/yo?user=jen")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("my-service"));
            given()
                    .when()
                    .port(PROXY_PORT)
                    .get("/users/jen")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("my-service"));
            given()
                    .when()
                    .port(PROXY_PORT)
                    .header(DevProxyServer.SESSION_HEADER, "jen")
                    .get("/stuff")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("my-service"));
        } finally {
            DevSpaceProxyRecorder.closeSession();
        }
        System.out.println("-------------------- After Shutdown GET REQUEST --------------------");
        given()
                .when()
                .port(PROXY_PORT)
                .get("/yo")
                .then()
                .statusCode(200)
                .contentType(equalTo("text/plain"))
                .body(equalTo("my-service"));
        given()
                .when()
                .port(PROXY_PORT)
                .get("/yo?user=john")
                .then()
                .statusCode(200)
                .contentType(equalTo("text/plain"))
                .body(equalTo("my-service"));
    }
}
