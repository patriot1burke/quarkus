package io.quarkus.depot.devproxy.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class Utils {
    public static <T> T await(long timeout, Future<T> future, String error) {
        CountDownLatch latch = new CountDownLatch(1);
        future.onComplete(event -> latch.countDown());
        try {
            latch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(error);
        }
        if (future.failed()) {
            throw new RuntimeException(error, future.cause());
        }
        return future.result();
    }
}
