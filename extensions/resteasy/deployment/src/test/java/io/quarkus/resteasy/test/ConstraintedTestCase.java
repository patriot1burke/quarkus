package io.quarkus.resteasy.test;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class ConstraintedTestCase {

    @RegisterExtension
    static QuarkusUnitTest runner = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ConstrainedResource.class)
                    .addAsResource("application-constrained.properties",
                            "application.properties"));

    @Test
    public void testRootResource() {
        RestAssured.when().get("/").then().body(Matchers.is("11111111112222222222333333333344444444445555555555"));
    }
}
