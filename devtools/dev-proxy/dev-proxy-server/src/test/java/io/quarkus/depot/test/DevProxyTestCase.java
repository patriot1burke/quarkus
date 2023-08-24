package io.quarkus.depot.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.depot.devproxy.server.DevProxyServer;
import io.quarkus.depot.devproxy.server.Service;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

@QuarkusTest
public class DevProxyTestCase {

    @Inject
    public Vertx vertx;

    static HttpServer myService;

    static WebClient client;

    @BeforeEach
    public void before() {
        if (myService != null)
            return;

        myService = vertx.createHttpServer();
        myService.requestHandler(request -> {
            request.response().setStatusCode(200).putHeader("Content-Type", "text/plain").end("my-service");
        }).listen(9091);

        client = WebClient.create(vertx);

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
            myService.close();
        if (client != null)
            client.close();
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

    }

    static HttpResponse<Buffer> awaitResult(HttpRequest<Buffer> request) throws Exception {
        return awaitResult(request, 100);
    }

    static HttpResponse<Buffer> awaitResult(HttpRequest<Buffer> request, long time) throws Exception {
        CountDownLatch testLatch = new CountDownLatch(1);
        Future<HttpResponse<Buffer>> futureResponse = request.send()
                .onSuccess(event -> testLatch.countDown())
                .onFailure(event -> testLatch.countDown());
        testLatch.await(time, TimeUnit.MILLISECONDS);
        return futureResponse.result();
    }

    static HttpResponse<Buffer> awaitResult(long time, Supplier<HttpRequest<Buffer>> supplier) throws Exception {
        return awaitResult(supplier.get(), time);
    }

    static HttpResponse<Buffer> awaitResult(Supplier<HttpRequest<Buffer>> supplier) throws Exception {
        return awaitResult(supplier.get(), 100);
    }

    @Test
    public void testGlobalSession() throws Exception {
        HttpResponse<Buffer> res = awaitResult(() -> client
                .post(8081, "localhost", DevProxyServer.CLIENT_API_PATH + "/connect/my-service"));
        Assertions.assertEquals(res.statusCode(), 204);
        String poll = res.getHeader(DevProxyServer.POLL_LINK);
        Assertions.assertNotNull(poll);
        AtomicBoolean keepAlive = new AtomicBoolean(true);
        client.post(8081, "localhost", poll).send().onSuccess(event -> {
            poll(event, keepAlive);
        });
        keepAlive.set(true); // only do one request
        System.out.println("-------------------- GET REQUEST --------------------");
        given()
                .when()
                .queryParam("_dp", "my-service")
                .get("/yo")
                .then()
                .statusCode(200)
                .contentType(equalTo("text/plain"))
                .body(equalTo("GET$/yo?_dp=my-service"));
        System.out.println("------------------ POST REQUEST ---------------------");
        given()
                .when()
                .queryParam("_dp", "my-service")
                .post("/hey")
                .then()
                .statusCode(200)
                .contentType(equalTo("text/plain"))
                .body(equalTo("POST$/hey?_dp=my-service"));
        given()
                .when()
                .delete(DevProxyServer.CLIENT_API_PATH + "/connect/my-service")
                .then()
                .statusCode(204);

    }

    private static void poll(HttpResponse<Buffer> pollResponse, AtomicBoolean keepAlive) {
        System.out.println("client poll");
        if (pollResponse.statusCode() != 200) {
            System.out.println("Client failed with : " + pollResponse.statusCode());
            return;
        }
        String method = pollResponse.getHeader(DevProxyServer.METHOD_HEADER);
        String uri = pollResponse.getHeader(DevProxyServer.URI_HEADER);
        String responsePath = pollResponse.getHeader(DevProxyServer.RESPONSE_LINK);
        client.post(8081, "localhost", responsePath)
                .addQueryParam("keepAlive", keepAlive.toString())
                .putHeader(DevProxyServer.STATUS_CODE_HEADER, "200")
                .putHeader(DevProxyServer.HEADER_FORWARD_PREFIX + "Content-Type", "text/plain")
                .sendBuffer(Buffer.buffer(method + "$" + uri))
                .onSuccess(event -> {
                    if (event.statusCode() == 200) {
                        poll(event, keepAlive);
                    }
                });

    }

}
