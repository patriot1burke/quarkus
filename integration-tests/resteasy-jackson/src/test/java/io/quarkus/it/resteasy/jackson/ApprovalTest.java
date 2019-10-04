package io.quarkus.it.resteasy.jackson;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class ApprovalTest {

    @Test
    void testEndpoint() {
        given()
                .header("Authorization", "Basic am9objpqb2hu")
                .body("{\"traveller\" : {\"firstName\" : \"John\",\"lastName\" : \"Doe\",\"email\" : \"john.doe@example.com\",\"nationality\" : \"American\",\"address\" : {\"street\" : \"main street\",\"city\" : \"Boston\",\"zipCode\" : \"10005\",\"country\" : \"US\"}}}")
                .contentType(ContentType.JSON)
                .when()
                .post("/approvals")
                .then()
                .statusCode(200);
    }

    @Test
    void testEndpoint2() {
        String output = given()
                .header("Authorization", "Basic am9objpqb2hu")
                .when()
                .get("/approvals")
                .then()
                .statusCode(200).extract().asString();
        System.out.println(output);
    }

}
