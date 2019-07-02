package io.quarkus.it.resteasy.netty;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/hello")
public class HelloResource {
    @GET
    @Produces("text/plain")
    public String hello() {
        return "hello world";
    }

    @POST
    @Produces("text/plain")
    @Consumes("text/plain")
    public String hello(String name) {
        return "Hello " + name;
    }

}
