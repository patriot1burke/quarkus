package io.quarkus.it.resteasy.netty;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class ResteasyNettyTest {
    @Test
    public void testHelloEndpoint() {
        RestAssured.port = 8080;
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(is("hello world"));

    }

}
