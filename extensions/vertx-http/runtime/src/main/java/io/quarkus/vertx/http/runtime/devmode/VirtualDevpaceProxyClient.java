package io.quarkus.vertx.http.runtime.devmode;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.*;
import io.quarkus.devspace.server.DevProxyServer;
import io.quarkus.netty.runtime.virtual.VirtualClientConnection;
import io.quarkus.netty.runtime.virtual.VirtualResponseHandler;
import io.quarkus.vertx.http.runtime.QuarkusHttpHeaders;
import io.quarkus.vertx.http.runtime.VertxHttpRecorder;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.core.streams.impl.InboundBuffer;

public class VirtualDevpaceProxyClient {
    protected static final Logger log = Logger.getLogger(VirtualDevpaceProxyClient.class);

    protected Vertx vertx;
    protected HttpClient proxyClient;
    protected String whoami;
    protected String sessionId;
    protected int numPollers = 1;
    protected volatile boolean running = true;
    protected volatile boolean shutdown = false;
    protected String pollLink;
    protected CountDownLatch workerShutdown;
    protected long pollTimeoutMillis = 1000;

    public boolean startGlobalSession() {
        return startSession(DevProxyServer.GLOBAL_PROXY_SESSION);
    }

    public boolean startSession(String sessionId) {
        String uri = DevProxyServer.CLIENT_API_PATH + "/connect?who=" + whoami + "&session=" + sessionId;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean();
        proxyClient.request(HttpMethod.POST, uri, event -> {
            if (event.failed()) {
                log.error("Could not connect to startSession", event.cause());
                latch.countDown();
                return;
            }
            HttpClientRequest request = event.result();
            request.send().onComplete(event1 -> {
                if (event1.failed()) {
                    log.error("Could not connect to startSession", event1.cause());
                    latch.countDown();
                    return;
                }
                HttpClientResponse response = event1.result();
                if (response.statusCode() != 204) {
                    response.bodyHandler(body -> {
                        log.error("Could not connect to startSession " + response.statusCode() + body.toString());
                        latch.countDown();
                    });
                    return;
                }
                try {
                    this.pollLink = response.getHeader(DevProxyServer.POLL_LINK);
                    for (int i = 0; i < numPollers; i++)
                        poll();
                    workerShutdown = new CountDownLatch(numPollers);
                    success.set(true);
                } finally {
                    latch.countDown();
                }
            });
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (!success.get()) {
            forcedShutdown();
            return false;
        }
        this.sessionId = sessionId;
        return true;
    }

    public void forcedShutdown() {
        shutdown = true;
        running = false;
        proxyClient.close();
    }

    protected void pollFailure(Throwable failure) {
        log.error("Poll failed", failure);
        workerOffline();
    }

    protected void pollFailure(String error) {
        log.error("Poll failed: " + error);
        workerOffline();
    }

    private void workerOffline() {
        workerShutdown.countDown();
    }

    protected void poll() {
        if (!running) {
            workerOffline();
            return;
        }
        proxyClient.request(HttpMethod.POST, pollLink)
                .onSuccess(request -> {
                    request.setTimeout(pollTimeoutMillis)
                            .send()
                            .onSuccess(this::handlePoll)
                            //.onSuccess(this::handlePollWaitBody)
                            .onFailure(this::pollFailure);

                })
                .onFailure(this::pollFailure);
    }

    private class NettyResponseHandler implements VirtualResponseHandler, ReadStream<Buffer> {

        final String responsePath;
        InboundBuffer<Buffer> queue;
        Buffer end = Buffer.buffer();
        Handler<Void> endHandler;

        public NettyResponseHandler(String responsePath, Vertx vertx) {
            this.responsePath = responsePath;
            queue = new InboundBuffer<>(vertx.getOrCreateContext());
            queue.pause();
        }

        @Override
        public ReadStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
            queue.exceptionHandler(handler);
            return this;
        }

        @Override
        public ReadStream<Buffer> handler(@Nullable Handler<Buffer> handler) {
            queue.handler((buf) -> {
                if (buf == end) {
                    if (endHandler != null) {
                        endHandler.handle(null);
                    }
                } else {
                    handler.handle(buf);
                }
            });
            return this;
        }

        @Override
        public ReadStream<Buffer> pause() {
            queue.pause();
            return this;
        }

        @Override
        public ReadStream<Buffer> resume() {
            queue.resume();
            return this;
        }

        @Override
        public ReadStream<Buffer> fetch(long amount) {
            queue.fetch(amount);
            return this;
        }

        @Override
        public ReadStream<Buffer> endHandler(@Nullable Handler<Void> endHandler) {
            this.endHandler = endHandler;
            return this;
        }

        @Override
        public void handleMessage(Object msg) {
            if (msg instanceof HttpResponse) {
                HttpResponse res = (HttpResponse) msg;
                proxyClient.request(HttpMethod.POST, responsePath + "?keepAlive=" + running)
                        .onFailure(exc -> {
                            log.error("Proxy handle response failure", exc);
                            workerOffline();
                        })
                        .onSuccess(pushRequest -> {
                            pushRequest.setTimeout(pollTimeoutMillis);
                            pushRequest.putHeader(DevProxyServer.STATUS_CODE_HEADER, Integer.toString(res.status().code()));

                            for (String name : res.headers().names()) {
                                final List<String> allForName = res.headers().getAll(name);
                                if (allForName == null || allForName.isEmpty()) {
                                    continue;
                                }

                                for (Iterator<String> valueIterator = allForName.iterator(); valueIterator.hasNext();) {
                                    String val = valueIterator.next();
                                    if (name.equalsIgnoreCase("Transfer-Encoding")
                                            && val.equals("chunked")) {
                                        continue; // ignore transfer encoding, chunked screws up message and response
                                    }
                                    pushRequest.headers().add(DevProxyServer.HEADER_FORWARD_PREFIX + name, val);
                                }
                            }
                            pushRequest.send(this)
                                    .onFailure(exc -> {
                                        log.error("Failed to push service response", exc);
                                        workerOffline();
                                    })
                                    .onSuccess(VirtualDevpaceProxyClient.this::handlePoll); // a successful push restarts poll
                        });
            }
            if (msg instanceof HttpContent) {
                queue.write(BufferImpl.buffer(((HttpContent) msg).content()));
            }
            if (msg instanceof FileRegion) {
                log.error("FileRegion not supported yet");
                throw new RuntimeException("FileRegion not supported yet");
            }
            if (msg instanceof LastHttpContent) {
                queue.write(end);
            }
        }

        @Override
        public void close() {

        }
    }

    private class NettyWriteStream implements WriteStream<Buffer> {
        VirtualClientConnection connection;

        public NettyWriteStream(VirtualClientConnection connection) {
            this.connection = connection;
        }

        private void writeHttpContent(Buffer data) {
            // todo getByteBuf copies the underlying byteBuf
            DefaultHttpContent content = new DefaultHttpContent(data.getByteBuf());
            connection.sendMessage(content);

        }

        @Override
        public WriteStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
            return this;
        }

        @Override
        public Future<Void> write(Buffer data) {
            writeHttpContent(data);
            Promise<Void> promise = Promise.promise();
            write(data, promise);
            return promise.future();
        }

        @Override
        public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
            writeHttpContent(data);
            handler.handle(Future.succeededFuture());
        }

        @Override
        public void end(Handler<AsyncResult<Void>> handler) {
            connection.sendMessage(LastHttpContent.EMPTY_LAST_CONTENT);
            handler.handle(Future.succeededFuture());
        }

        @Override
        public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
            return this;
        }

        @Override
        public boolean writeQueueFull() {
            return false;
        }

        @Override
        public WriteStream<Buffer> drainHandler(@Nullable Handler<Void> handler) {
            return this;
        }
    }

    protected void handlePoll(HttpClientResponse pollResponse) {
        pollResponse.pause();
        log.debug("------ handlePoll");
        int proxyStatus = pollResponse.statusCode();
        if (proxyStatus == 408) {
            poll();
            return;
        } else if (proxyStatus == 204) {
            // keepAlive = false sent back
            workerOffline();
            return;
        } else if (proxyStatus != 200) {
            pollResponse.bodyHandler(body -> {
                pollFailure(body.toString());
            });
            return;
        }

        String method = pollResponse.getHeader(DevProxyServer.METHOD_HEADER);
        String uri = pollResponse.getHeader(DevProxyServer.URI_HEADER);
        String responsePath = pollResponse.getHeader(DevProxyServer.RESPONSE_LINK);
        NettyResponseHandler handler = new NettyResponseHandler(responsePath, vertx);
        VirtualClientConnection connection = VirtualClientConnection.connect(handler, VertxHttpRecorder.VIRTUAL_HTTP,
                null);

        QuarkusHttpHeaders quarkusHeaders = new QuarkusHttpHeaders();
        // add context specific things
        io.netty.handler.codec.http.HttpMethod httpMethod = io.netty.handler.codec.http.HttpMethod.valueOf(method);

        DefaultHttpRequest nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                httpMethod, uri,
                quarkusHeaders);
        pollResponse.headers().forEach((key, val) -> {
            log.debugv("Poll response header: {0} : {1}", key, val);
            int idx = key.indexOf(DevProxyServer.HEADER_FORWARD_PREFIX);
            if (idx == 0) {
                String headerName = key.substring(DevProxyServer.HEADER_FORWARD_PREFIX.length());
                nettyRequest.headers().add(headerName, val);
            } else if (key.equalsIgnoreCase("Content-Length")) {
                nettyRequest.headers().add("Content-Length", val);
            }
        });
        if (!nettyRequest.headers().contains(HttpHeaderNames.HOST)) {
            nettyRequest.headers().add(HttpHeaderNames.HOST, "localhost");
        }

        connection.sendMessage(nettyRequest);
        pollResponse.pipeTo(new NettyWriteStream(connection));
    }

    private void deletePushResponse(String link) {
        if (link == null) {
            workerOffline();
            return;
        }
        proxyClient.request(HttpMethod.DELETE, link)
                .onFailure(event -> workerOffline())
                .onSuccess(request -> request.send().onComplete(event -> workerOffline()));
    }

    public void shutdown() {
        if (shutdown) {
            return;
        }
        try {
            running = false;
            try {
                // give time for workers to finish
                workerShutdown.await(5, TimeUnit.SECONDS);
            } catch (Throwable ignored) {

            }
            // delete session
            CountDownLatch latch = new CountDownLatch(1);
            if (sessionId != null) {
                String uri = DevProxyServer.CLIENT_API_PATH + "/connect?session=" + sessionId;
                proxyClient.request(HttpMethod.DELETE, uri)
                        .onFailure(event -> {
                            log.error("Failed to delete sesssion on shutdown", event);
                            latch.countDown();
                        })
                        .onSuccess(request -> request.send()
                                .onComplete(event -> {
                                    if (event.failed()) {
                                        log.error("Failed to delete sesssion on shutdown", event.cause());
                                    }
                                    latch.countDown();
                                }));

            }
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            proxyClient.close();
        } finally {
            shutdown = true;
        }
    }

}
