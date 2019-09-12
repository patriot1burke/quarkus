package io.quarkus.it.resteasy.jackson;

import io.quarkus.vertx.runtime.VertxProducer;
import io.reactivex.Single;
import io.vertx.core.Context;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/ping")
public class AsyncResource {

    @Inject
    VertxProducer vertx;

    @Path("async")
    @GET
    public Single<String> hello8(){
        System.err.println("Creating client: " + (vertx != null));
        WebClientOptions options = new WebClientOptions();
        options.setSsl(true);
        options.setTrustAll(true);
        options.setVerifyHost(false);
        WebClient client = WebClient.create(vertx.rx(), options);
        Single<HttpResponse<Buffer>> responseHandler = client.get(443,
                "www.google.com",
                "/robots.txt").rxSend();

        System.err.println("Created client");
        return responseHandler.map(body -> {
            System.err.println("Got body in io thread? " + Context.isOnEventLoopThread());
            return body.body().toString();
        }).doAfterTerminate(() -> client.close());
    }

    @GET
    public String nonAsync() {
        return "hello";
    }
}
