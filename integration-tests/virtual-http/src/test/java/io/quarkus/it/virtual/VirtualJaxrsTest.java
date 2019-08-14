package io.quarkus.it.virtual;

import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class VirtualJaxrsTest {

    //@Test
    public void test() {
        RestAssured.when().get("/").then()
                .body(containsString("hello"));
    }

}
