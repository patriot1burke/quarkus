package io.quarkus.devspace.test;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import io.quarkus.devspace.server.PathParamSessionMatcher;

public class MatchTest {
    @Test
    public void testPathParam() {
        PathParamSessionMatcher router = new PathParamSessionMatcher("/foo/bar/[]");
        Assertions.assertEquals("bill", router.match("/foo/bar/bill"));
        Assertions.assertEquals("bill", router.match("/foo/bar/bill/other/stuff"));
        Assertions.assertEquals("bill", router.match("/foo/bar/bill/"));
    }

}
