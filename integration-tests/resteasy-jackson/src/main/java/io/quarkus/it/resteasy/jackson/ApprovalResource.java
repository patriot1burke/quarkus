package io.quarkus.it.resteasy.jackson;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@Path("/approvals")
public class ApprovalResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response approval(Approval approval, @Context SecurityContext sec) {
        System.out.println(sec.getUserPrincipal().getName());
        return Response.ok().build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Approval approval(@Context SecurityContext sec) {
        System.out.println(sec.getUserPrincipal().getName());
        Approval approval = new Approval();
        Approval.Person person = new Approval.Person();
        approval.setTraveller(person);
        person.setFirstName("Bill");
        person.setLastName("Burke");
        person.setEmail("bburke@redhat.com");
        person.setNationality("American");
        Approval.Address address = new Approval.Address();
        address.setStreet("main street");
        address.setCity("boston");
        address.setZipCode("02115");
        address.setCountry("USA");
        person.setAddress(address);
        return approval;
    }
}
