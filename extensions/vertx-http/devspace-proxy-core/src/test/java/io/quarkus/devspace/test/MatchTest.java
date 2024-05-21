package io.quarkus.devspace.test;

import io.quarkus.devspace.server.PathParamSessionRouter;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class MatchTest {
    @Test
    public void testPathParam() {
        PathParamSessionRouter router = new PathParamSessionRouter("/foo/bar/<service>");
        Assertions.assertEquals("bill", router.match("/foo/bar/bill"));
        Assertions.assertEquals("bill", router.match("/foo/bar/bill/other/stuff"));
        Assertions.assertEquals("bill", router.match("/foo/bar/bill/"));
    }

}
