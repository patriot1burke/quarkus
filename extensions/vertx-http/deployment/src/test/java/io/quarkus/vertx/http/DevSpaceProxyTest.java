package io.quarkus.vertx.http;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devspace.ProxyUtils;
import io.quarkus.devspace.client.DevProxyClient;
import io.quarkus.devspace.server.DevProxyServer;
import io.quarkus.devspace.server.ServiceConfig;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public class DevSpaceProxyTest {

    public static final int SERVICE_PORT = 9091;
    public static final int PROXY_PORT = 9092;
    @Inject
    public Vertx vertx;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar.addClasses(DevSpaceProxyTest.RouteProducer.class));

    public static DevProxyServer proxyServer;
    public static HttpServer proxy;

    static HttpServer myService;

    @Singleton
    public static class RouteProducer {
        void observeRouter(@Observes Router router) {
            router.route().handler(
                    request -> request.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end("local"));
        }

    }

    @BeforeEach
    public void before() {
        if (myService != null)
            return;

        myService = vertx.createHttpServer();
        myService.requestHandler(request -> {
            request.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end("my-service");
        }).listen(SERVICE_PORT);

        proxy = vertx.createHttpServer();
        proxyServer = new DevProxyServer();
        Router proxyRouter = Router.router(vertx);
        ServiceConfig config = new ServiceConfig("my-service", "localhost", SERVICE_PORT);
        proxyServer.init(vertx, proxyRouter, config);
        proxy.requestHandler(proxyRouter).listen(PROXY_PORT);

    }

    @AfterAll
    public static void after() {
        if (myService != null) {
            ProxyUtils.await(1000, myService.close());
            ProxyUtils.await(1000, proxy.close());
        }
    }

    @Test
    public void testProxy() {
        // invoke service directly
        given()
                .when()
                .port(SERVICE_PORT)
                .get("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
        given()
                .when()
                .port(SERVICE_PORT)
                .body("hello")
                .contentType("text/plain")
                .post("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
        // invoke local directly
        given()
                .when()
                .get("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("local"));
        given()
                .when()
                .body("hello")
                .contentType("text/plain")
                .post("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("local"));
        // invoke proxy
        given()
                .when()
                .port(PROXY_PORT)
                .get("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
        given()
                .when()
                .port(PROXY_PORT)
                .body("hello")
                .contentType("text/plain")
                .post("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
    }

    //@Test
    public void testGlobalSession() throws Exception {
        DevProxyClient client = DevProxyClient.create(vertx)
                .proxy("localhost", 8081, false)
                .service("my-service", "localhost", 9092, false)
                .whoami("bill")
                .build();
        Assertions.assertTrue(client.startGlobalSession());
        try {
            System.out.println("------------------ POST REQUEST BODY ---------------------");
            given()
                    .when()
                    .contentType("text/plain")
                    .body("hello")
                    .post("/hey")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("-------------------- GET REQUEST --------------------");
            given()
                    .when()
                    .get("/yo")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("------------------ POST REQUEST NO BODY ---------------------");
            given()
                    .when()
                    .post("/hey")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
        } finally {
            client.shutdown();
        }
        System.out.println("-------------------- After Shutdown GET REQUEST --------------------");
        given()
                .when()
                .get("/yo")
                .then()
                .statusCode(200)
                .contentType(equalTo("text/plain"))
                .body(equalTo("my-service"));
    }
}
