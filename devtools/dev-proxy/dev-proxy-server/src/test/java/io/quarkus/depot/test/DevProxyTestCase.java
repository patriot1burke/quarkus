package io.quarkus.depot.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.depot.devproxy.ProxyUtils;
import io.quarkus.depot.devproxy.client.DevProxyClient;
import io.quarkus.depot.devproxy.server.DevProxyServer;
import io.quarkus.depot.devproxy.server.HostNameServiceRoutingStrategy;
import io.quarkus.depot.devproxy.server.PathParamServiceRoutingStrategy;
import io.quarkus.depot.devproxy.server.QueryParamServiceRoutingStrategy;
import io.quarkus.depot.devproxy.server.Service;
import io.quarkus.depot.devproxy.server.ServiceRoutingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

@QuarkusTest
public class DevProxyTestCase {

    @Inject
    public Vertx vertx;

    @Inject
    public DevProxyServer server;

    static HttpServer myService;

    static HttpServer localService;

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

        // initialize
        testCreateService();
    }

    private void testCreateService() {
        Service service = new Service("my-service", "localhost", 9091);

        given()
                .contentType("application/json")
                .accept("application/json")
                .body(service)
                .when()
                .post(DevProxyServer.SERVICES_API_PATH)
                .then()
                .statusCode(201);

        given()
                .accept("application/json")
                .when()
                .get(DevProxyServer.SERVICES_API_PATH)
                .then()
                .statusCode(200)
                .body("my-service.name", equalTo("my-service"))
                .body("my-service.host", equalTo("localhost"))
                .body("my-service.port", equalTo(9091));
    }

    @AfterAll
    public static void after() {
        if (myService != null)
            ProxyUtils.await(1000, myService.close());
        if (localService != null)
            ProxyUtils.await(1000, localService.close());
    }

    @Test
    public void testRegex() throws Exception {
        String pattern = "(?<service>\\w+)\\.mycompany\\.com";
        Pattern compile = Pattern.compile(pattern);
        Matcher matcher = compile.matcher("myservice.mycompany.com");
        Assertions.assertTrue(matcher.matches());
        Assertions.assertEquals("myservice", matcher.group("service"));

        pattern = "/(?<service>[^/]+).*";
        compile = Pattern.compile(pattern);
        matcher = compile.matcher("/myservice");
        Assertions.assertTrue(matcher.matches());
        Assertions.assertEquals("myservice", matcher.group("service"));
        matcher = compile.matcher("/myservice/");
        Assertions.assertTrue(matcher.matches());
        Assertions.assertEquals("myservice", matcher.group("service"));
        matcher = compile.matcher("/myservice/foo/bar");
        Assertions.assertTrue(matcher.matches());
        Assertions.assertEquals("myservice", matcher.group("service"));

        pattern = "/(?<service>[^/]+).*.*";
        compile = Pattern.compile(pattern);
        matcher = compile.matcher("/myservice");
        Assertions.assertTrue(matcher.matches());
        Assertions.assertEquals("myservice", matcher.group("service"));
        matcher = compile.matcher("/myservice/");
        Assertions.assertTrue(matcher.matches());
        Assertions.assertEquals("myservice", matcher.group("service"));
        matcher = compile.matcher("/myservice/foo/bar");
        Assertions.assertTrue(matcher.matches());
        Assertions.assertEquals("myservice", matcher.group("service"));

        pattern = "localhost\\.(?<service>[^:]+)(:\\d+)?";
        compile = Pattern.compile(pattern);
        matcher = compile.matcher("localhost.my-service:8081");
        Assertions.assertTrue(matcher.matches());
        Assertions.assertEquals("my-service", matcher.group("service"));
        matcher = compile.matcher("localhost.my-service");
        Assertions.assertTrue(matcher.matches());
        Assertions.assertEquals("my-service", matcher.group("service"));

    }

    @Test
    public void testProxyQueryParamStrategy() {
        ServiceRoutingStrategy oldStrategy = server.getRoutingStrategy();
        try {
            server.setRoutingStrategy(new QueryParamServiceRoutingStrategy("_dp"));

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
            given()
                    .when()
                    .queryParam("_dp", "my-service")
                    .get("/yo")
                    .then()
                    .statusCode(200)
                    .body(equalTo("my-service"));
            given()
                    .when()
                    .get("/yo")
                    .then()
                    .statusCode(404);
            given()
                    .when()
                    .queryParam("_dp", "nowhere")
                    .get("/yo")
                    .then()
                    .statusCode(404);
            given()
                    .when()
                    .queryParam("_dp", "my-service")
                    .body("hello")
                    .contentType("text/plain")
                    .post("/yo")
                    .then()
                    .statusCode(200)
                    .body(equalTo("my-service"));
        } finally {
            server.setRoutingStrategy(oldStrategy);
        }
    }

    @Test
    public void testProxyPathParamStrategy() {
        ServiceRoutingStrategy oldStrategy = server.getRoutingStrategy();
        try {
            server.setRoutingStrategy(new PathParamServiceRoutingStrategy());

            given()
                    .when()
                    .get("/my-service/yo")
                    .then()
                    .statusCode(200)
                    .body(equalTo("my-service"));
            given()
                    .when()
                    .body("hello")
                    .contentType("text/plain")
                    .post("/my-service/yo")
                    .then()
                    .statusCode(200)
                    .body(equalTo("my-service"));
            given()
                    .when()
                    .get("/yo")
                    .then()
                    .statusCode(404);
            given()
                    .when()
                    .get("/nowhere/yo")
                    .then()
                    .statusCode(404);
        } finally {
            server.setRoutingStrategy(oldStrategy);
        }
    }

    @Test
    public void testHostNameStrategy() {
        WebClient client = WebClient.create(vertx);
        ServiceRoutingStrategy oldStrategy = server.getRoutingStrategy();
        try {
            server.setRoutingStrategy(new HostNameServiceRoutingStrategy("localhost\\.(?<service>[^:]+)"));
            HttpResponse<Buffer> res = ProxyUtils.await(100,
                    client.get(8081, "localhost", "/yo")
                            .virtualHost("localhost.my-service")
                            .send());
            Assertions.assertEquals(200, res.statusCode());
            Assertions.assertEquals(res.bodyAsString(), "my-service");

        } finally {
            server.setRoutingStrategy(oldStrategy);
            client.close();
        }
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
                    .queryParam("_dp", "my-service")
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
                    .queryParam("_dp", "my-service")
                    .get("/yo")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
            System.out.println("------------------ POST REQUEST NO BODY ---------------------");
            given()
                    .when()
                    .queryParam("_dp", "my-service")
                    .post("/hey")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("local"));
        } finally {
            client.shutdown();
            System.out.println("-------------------- After Shutdown GET REQUEST --------------------");
            given()
                    .when()
                    .queryParam("_dp", "my-service")
                    .get("/yo")
                    .then()
                    .statusCode(200)
                    .contentType(equalTo("text/plain"))
                    .body(equalTo("my-service"));
        }
    }

    private static void poll(HttpClient client, HttpClientResponse pollResponse, AtomicBoolean keepAlive) {
        System.out.println("========== client poll");
        pollResponse.body()
                .onFailure(event -> System.out.println(" FAILED TO GET POLL RESPONSE BODY!!!"))
                .onSuccess(buffer -> {
                    String bd = buffer.toString();
                    if (bd.isEmpty())
                        bd = "EMPTY";
                    String body = bd;
                    System.out.println("poll response Body: " + body);
                    if (pollResponse.statusCode() != 200) {
                        System.out.println("Client failed with : " + pollResponse.statusCode());
                        return;
                    }
                    String method = pollResponse.getHeader(DevProxyServer.METHOD_HEADER);
                    String uri = pollResponse.getHeader(DevProxyServer.URI_HEADER);
                    String responsePath = pollResponse.getHeader(DevProxyServer.RESPONSE_LINK);

                    client.request(HttpMethod.POST, 8081, "localhost", responsePath + "?keepAlive=" + keepAlive.get())
                            .onSuccess(req -> {
                                req.putHeader(DevProxyServer.STATUS_CODE_HEADER, "200")
                                        .putHeader(DevProxyServer.HEADER_FORWARD_PREFIX + "Content-Type", "text/plain")
                                        .send(body)
                                        .onSuccess(event -> {
                                            if (event.statusCode() == 200) {
                                                poll(client, event, keepAlive);
                                            }
                                        });

                            });
                });

    }
}
