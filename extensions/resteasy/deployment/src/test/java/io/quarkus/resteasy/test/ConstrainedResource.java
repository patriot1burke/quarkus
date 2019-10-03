package io.quarkus.resteasy.test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/")
public class ConstrainedResource {

    @GET
    public String root() {
        return "11111111112222222222333333333344444444445555555555";
    }
}
