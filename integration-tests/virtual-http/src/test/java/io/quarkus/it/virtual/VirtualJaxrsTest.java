package io.quarkus.it.virtual;

import io.quarkus.netty.runtime.virtual.VirtualConnection;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vertx.web.runtime.VertxWebRecorder;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
public class VirtualJaxrsTest {

    //@Test
    public void test() {
        RestAssured.when().get("/").then()
                .body(containsString("hello"));
    }


}
