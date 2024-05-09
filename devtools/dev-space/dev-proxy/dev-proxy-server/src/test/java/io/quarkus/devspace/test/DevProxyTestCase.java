package io.quarkus.devspace.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.devspace.ProxyUtils;
import io.quarkus.devspace.client.DevProxyClient;
import io.quarkus.devspace.server.DevProxyServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;

@QuarkusTest
@TestProfile(DevProxyTestCase.ConfigOverrides.class)
public class DevProxyTestCase {

    @Inject
    public Vertx vertx;

    @Inject
    public DevProxyServer server;

    static HttpServer myService;

    static HttpServer localService;

    public static class ConfigOverrides implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "devspace.host", "localhost",
                    "devspace.name", "my-service",
                    "devspace.port", "9091"
            //,"quarkus.log.level", "DEBUG"
            );
        }
    }

    @BeforeEach
    public void before() {
        if (myService != null)
            return;

        myService = vertx.createHttpServer();
        myService.requestHandler(request -> {
            request.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end("my-service");
        }).listen(9091);

        localService = vertx.createHttpServer();
        localService.requestHandler(request -> {
            request.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/plain")
                    .end("local");
        }).listen(9092);

    }

    @AfterAll
    public static void after() {
        if (myService != null)
            ProxyUtils.await(1000, myService.close());
        if (localService != null)
            ProxyUtils.await(1000, localService.close());
    }

    @Test
    public void testProxy() {
        // invoke service directly
        given()
                .when()
                .port(9091)
                .get("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
        given()
                .when()
                .port(9091)
                .body("hello")
                .contentType("text/plain")
                .post("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
        // invoke local directly
        given()
                .when()
                .port(9092)
                .get("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("local"));
        given()
                .when()
                .port(9092)
                .body("hello")
                .contentType("text/plain")
                .post("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("local"));
        // invoke proxy
        given()
                .when()
                .get("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
        given()
                .when()
                .body("hello")
                .contentType("text/plain")
                .post("/yo")
                .then()
                .statusCode(200)
                .body(equalTo("my-service"));
    }

    @Test
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
