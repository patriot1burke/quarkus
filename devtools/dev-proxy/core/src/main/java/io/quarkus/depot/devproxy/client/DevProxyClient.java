package io.quarkus.depot.devproxy.client;

import io.quarkus.depot.devproxy.server.DevProxyServer;
import io.vertx.core.AsyncResult;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import org.jboss.logging.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DevProxyClient {
    protected static final Logger log = Logger.getLogger(DevProxyServer.class);

    protected HttpClient proxyClient;
    protected HttpClient serviceClient;
    protected String proxyHost;
    protected int proxyPort;
    protected String serviceHost;
    protected int servicePort;
    protected String whoami;
    protected String sessionId;
    protected int numPollers = 1;
    protected boolean keepAlive = true;
    protected volatile boolean running = true;
    protected String service;
    protected String pollLink;
    protected AtomicInteger workersRunning;

    protected CountDownLatch shutdown = new CountDownLatch(1);

    public void startGlobalSession(String sessionId) throws Exception {
        startSession(DevProxyServer.GLOBAL_PROXY_SESSION);
    }
    public void startSession(String sessionId) throws Exception {
        serviceInvocations = new Semaphore(numPollers);
        String uri = DevProxyServer.CLIENT_API_PATH + "/connect/" + service + "?who=" + whoami + "&session=" + sessionId;

        proxyClient.request(HttpMethod.POST, uri, event -> {
            if (event.failed()) {
                failure(event, "Could not connect to startSession");
                return;
            }
            HttpClientRequest request = event.result();
            request.send().onComplete(event1 -> {
                if (event1.failed()) {
                    failure(event1, "Could not connect to startSession");
                    return;
                }
                HttpClientResponse response = event1.result();
                if (response.statusCode() != 204) {
                    failure("Could not connect to startSession", response);
                }
                this.pollLink = response.getHeader(DevProxyServer.POLL_LINK);
                workersRunning = new AtomicInteger(numPollers);
                for (int i = 0; i < numPollers; i++) poll();
            });
        });
    }

    private void failure(AsyncResult<?> event, String message) {
        log.error(message, event.cause());
        failureShutdown();
    }

    private void failureShutdown() {
        shutdown.countDown();
    }

    private void failure(String message, HttpClientResponse response) {
        response.bodyHandler(body -> {
            log.error(message + ": " + body.toString());
            failureShutdown();
        });
    }

    protected void pollFailure(Throwable failure) {
        log.error("Poll failed", failure);
        workerFailure();
    }
    protected void pollFailure(String error) {
        log.error("Poll failed: " + error);
        workerFailure();
    }

    private void workerFailure() {
        workersRunning.decrementAndGet();
        synchronized(this) {
            if (workersRunning.get() == 0) {
                failureShutdown();
            }
        }
    }

    protected void poll() {
        if (!running) {
            return;
        }
        proxyClient.request(HttpMethod.POST, pollLink)
                .onSuccess(request -> {
                    request.setTimeout(DevProxyServer.POLL_TIMEOUT)
                            .send()
                            .onSuccess(this::handlePoll)
                            .onFailure(this::pollFailure);

                })
                .onFailure(this::pollFailure);
    }

    protected void handlePoll(HttpClientResponse pollResponse) {
        int proxyStatus = pollResponse.statusCode();
        if (proxyStatus == 408) {
            poll();
            return;
        } else if (proxyStatus != 200) {
            pollResponse.bodyHandler(body -> {
                pollFailure(body.toString());
            });
            return;
        }

        String method = pollResponse.getHeader(DevProxyServer.METHOD_HEADER);
        String uri = pollResponse.getHeader(DevProxyServer.URI_HEADER);
        serviceClient.request(HttpMethod.valueOf(method), uri)
                .onFailure(exc -> {
                    log.error("Service connect failure", exc);
                    workerFailure();
                })
                .onSuccess(serviceRequest -> {
                    invokeService(pollResponse, serviceRequest);
                })
        ;
    }


    private void invokeService(HttpClientResponse pollResponse, HttpClientRequest serviceRequest) {
        serviceRequest.setTimeout(1000 * 1000); // long timeout as there might be a debugger session
        pollResponse.headers().forEach((key, val) -> {
            int idx = key.indexOf(DevProxyServer.HEADER_FORWARD_PREFIX);
            if (idx == 0) {
                String headerName = key.substring(DevProxyServer.HEADER_FORWARD_PREFIX.length());
                serviceRequest.headers().add(headerName, val);
            } else if (key.equalsIgnoreCase("Content-Length")) {
                serviceRequest.headers().add("Content-Length", val);
            }
        });
        serviceRequest.send(pollResponse)
                .onFailure(exc -> {
                    log.error("Service send failure", exc);
                    workerFailure();
                })
                .onSuccess(serviceResponse -> {
                    String responsePath = pollResponse.getHeader(DevProxyServer.RESPONSE_LINK);
                    handleServiceResponse(responsePath, serviceResponse);
                });
    }

    private void handleServiceResponse(String responsePath, HttpClientResponse serviceResponse) {
        proxyClient.request(HttpMethod.POST, responsePath)
                .onFailure(exc -> {
                    log.error("Proxy handle response failure", exc);
                    workerFailure();
                })
                .onSuccess(pushRequest -> {
                    pushRequest.setTimeout(DevProxyServer.POLL_TIMEOUT * 2);
                    pushRequest.putHeader(DevProxyServer.STATUS_CODE_HEADER, Integer.toString(serviceResponse.statusCode()));
                    serviceResponse.headers().forEach((key, val) ->
                            pushRequest.headers().add(DevProxyServer.HEADER_FORWARD_PREFIX + key, val)
                    );
                    pushRequest.send(serviceResponse)
                            .onFailure(exc -> {
                                log.error("Failed to push service response", exc);
                                workerFailure();
                            })
                            .onSuccess(this::handlePoll); // a successful push restarts poll
                });
    }




    public void shutdown() {
        running = false;
        proxyClient.close();
        serviceClient.close();
    }


}
