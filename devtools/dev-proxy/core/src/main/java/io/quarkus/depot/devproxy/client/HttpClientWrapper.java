package io.quarkus.depot.devproxy.client;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HttpClientWrapper {
    private int timeout;
    private HttpClient client;

    public HttpClientWrapper(int timeout, HttpClient client) {
        this.timeout = timeout;
        this.client = client;
    }

    public HttpClientRequest get(String uri) {
        return request(uri, HttpMethod.GET);
    }
    public HttpClientRequest post(String uri) {
        return request(uri, HttpMethod.POST);
    }
    public HttpClientRequest delete(String uri) {
        return request(uri, HttpMethod.DELETE);
    }

    public HttpClient client() {
        return client;
    }



    private HttpClientRequest request(String uri, HttpMethod method) {
        CountDownLatch testLatch = new CountDownLatch(1);
        Future<HttpClientRequest> future = client
                .request(method, uri)
                .onSuccess(event -> testLatch.countDown())
                .onFailure(exception -> testLatch.countDown());
        try {
            testLatch.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Connection failed, timeout");
        }
        if (future.failed()) {
            throw new RuntimeException("Connection failed", future.cause());
        }
        return future.result();
    }

}
