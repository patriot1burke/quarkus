package io.quarkus.depot.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.depot.devproxy.ProxyUtils;
import io.quarkus.depot.devproxy.client.DevProxyClient;
import io.quarkus.depot.devproxy.server.DevProxyServer;
import io.quarkus.depot.devproxy.server.Service;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;

@QuarkusTest
public class DevProxyTestCase {

    @Inject
    public Vertx vertx;

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
            request.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end("local");
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
    public void testServiceApiAndProxy() {

        given()
                .when()
                .port(9091)
                .get("/yo")
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
                .body("hello")
                .contentType("text/plain")
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

    //@Test
    public void testPostBody() throws Exception {
        HttpClient client = vertx.createHttpClient();
        CountDownLatch latch1 = new CountDownLatch(1);
        Future<HttpClientRequest> futureReq = client.request(HttpMethod.POST, 8081,
                "localhost", DevProxyServer.CLIENT_API_PATH + "/connect/my-service")
                .onComplete(event -> latch1.countDown());
        latch1.await();
        HttpClientRequest req = futureReq.result();
        CountDownLatch latch2 = new CountDownLatch(1);
        Future<HttpClientResponse> futureRes = req.send()
                .onComplete(event -> latch2.countDown());
        latch2.await();
        HttpClientResponse res = futureRes.result();
        String poll = res.getHeader(DevProxyServer.POLL_LINK);
        Assertions.assertNotNull(poll);
        AtomicBoolean keepAlive = new AtomicBoolean(true);
        client.request(HttpMethod.POST, 8081, "localhost", poll)
                .onSuccess(pollReq -> pollReq.send().onSuccess(pollRes -> poll(client, pollRes, keepAlive)));
        keepAlive.set(true);
        System.out.println("------------------ POST REQUEST ---------------------");
        given()
                .when()
                .queryParam("_dp", "my-service")
                .contentType("text/plain")
                .body("hello")
                .post("/hey")
                .then()
                .statusCode(200)
                .contentType(equalTo("text/plain"))
                .body(equalTo("hello"));
        keepAlive.set(false); // only do one request
        System.out.println("------------------ POST REQUEST NO BODY ---------------------");
        given()
                .when()
                .queryParam("_dp", "my-service")
                .post("/hey")
                .then()
                .statusCode(200)
                .contentType(equalTo("text/plain"))
                .body(equalTo("EMPTY"));
        given()
                .when()
                .delete(DevProxyServer.CLIENT_API_PATH + "/connect/my-service")
                .then()
                .statusCode(204);

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
