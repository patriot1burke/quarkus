package io.quarkus.it.resteasy.jackson;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AsyncTest {
    @Test
    void testAsync() {
        given()
                .when().get("/async")
                .then()
                .statusCode(200)
                .body(containsString("/patents"));
    }

}
