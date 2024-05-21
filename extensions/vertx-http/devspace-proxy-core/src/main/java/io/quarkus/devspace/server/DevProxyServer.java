package io.quarkus.devspace.server;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.Pipe;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.HttpProxy;

public class DevProxyServer {
    public class ProxySession {
        final BlockingQueue<RoutingContext> queue = new LinkedBlockingQueue<>();
        final ConcurrentHashMap<String, RoutingContext> responsePending = new ConcurrentHashMap<>();
        final ServiceProxy proxy;
        final long timerId;
        final String sessionId;
        final String who;

        volatile boolean running = true;
        volatile long lastPoll;
        AtomicLong requestId = new AtomicLong(System.currentTimeMillis());

        ProxySession(ServiceProxy proxy, String sessionId, String who) {
            timerId = vertx.setPeriodic(POLL_TIMEOUT, this::timerCallback);
            this.proxy = proxy;
            this.sessionId = sessionId;
            this.who = who;
        }

        void timerCallback(Long t) {
            checkIdle();
        }

        private void checkIdle() {
            if (!running)
                return;
            if (System.currentTimeMillis() - lastPoll > POLL_TIMEOUT) {
                log.warnv("Shutting down session {0} due to timeout.", sessionId);
                shutdown();
            }
        }

        String queueResponse(RoutingContext ctx) {
            String requestId = Long.toString(this.requestId.incrementAndGet());
            responsePending.put(requestId, ctx);
            return requestId;
        }

        RoutingContext dequeueResponse(String requestId) {
            return responsePending.remove(requestId);
        }

        synchronized void shutdown() {
            if (!running)
                return;
            running = false;
            proxy.sessions.remove(this.sessionId);
            vertx.cancelTimer(timerId);
            while (!queue.isEmpty()) {
                List<RoutingContext> requests = new ArrayList<>();
                queue.drainTo(requests);
                requests.stream().forEach((ctx) -> proxy.proxy.handle(ctx.request()));
            }
            try {
                queue.put(END_SENTINEL);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        void pollStarted() {
            lastPoll = System.currentTimeMillis();
        }

        void pollProcessing() {
            lastPoll = System.currentTimeMillis();
        }

        void pollEnded() {
            lastPoll = System.currentTimeMillis();
        }

        synchronized void pollDisconnect() {
            if (!running) {
                return;
            }
            checkIdle();
        }

    }

    public class ServiceProxy {
        public ServiceProxy(ServiceConfig service) {
            this.config = service;
            this.proxy = HttpProxy.reverseProxy(client);
            proxy.origin(service.getPort(), service.getHost());
        }

        final ServiceConfig config;
        final HttpProxy proxy;
        final Map<String, ProxySession> sessions = new ConcurrentHashMap<>();

        void shutdown() {
            for (ProxySession session : sessions.values()) {
                session.shutdown();
            }
        }
    }

    public static final String CLIENT_API_PATH = "/_dev_proxy_client_";
    public static final String GLOBAL_PROXY_SESSION = "_depot_global";
    public static final String SESSION_HEADER = "X-Depot-Proxy-Session";
    public static final String HEADER_FORWARD_PREFIX = "X-Depot-Fwd-";
    public static final String STATUS_CODE_HEADER = "X-Depot-Status-Code";
    public static final String METHOD_HEADER = "X-Depot-Method";
    public static final String URI_HEADER = "X-Depot-Uri";
    public static final String REQUEST_ID_HEADER = "X-Depot-Request-Id";
    public static final String RESPONSE_LINK = "X-Depot-Response-Path";
    public static final String POLL_LINK = "X-Depot-Poll-Path";
    public static final String PROXY_API_PATH = "/_dev_proxy_api_";

    protected long POLL_TIMEOUT = 5000;
    protected static final Logger log = Logger.getLogger(DevProxyServer.class);
    protected ServiceProxy service;
    protected Vertx vertx;
    protected Router router;
    protected HttpClient client;

    public void init(Vertx vertx, Router router, ServiceConfig config) {
        this.vertx = vertx;
        this.router = router;
        client = vertx.createHttpClient();
        // API routes
        router.route().handler((context) -> {
            if (context.get("continue-sent") == null) {
                String expect = context.request().getHeader(HttpHeaderNames.EXPECT);
                if (expect != null && expect.equalsIgnoreCase("100-continue")) {
                    context.put("continue-sent", true);
                    context.response().writeContinue();
                }
            }
            context.next();
        });
        router.route(DevProxyServer.PROXY_API_PATH + "/*").handler(BodyHandler.create());
        // CLIENT API
        router.route(CLIENT_API_PATH + "/poll/session/:session").method(HttpMethod.POST).handler(this::pollNext);
        router.route(CLIENT_API_PATH + "/connect").method(HttpMethod.POST).handler(this::clientConnect);
        router.route(CLIENT_API_PATH + "/connect").method(HttpMethod.DELETE).handler(this::deleteClientConnection);
        router.route(CLIENT_API_PATH + "/push/response/session/:session/request/:request")
                .method(HttpMethod.POST)
                .handler(this::pushResponse);
        router.route(CLIENT_API_PATH + "/push/response/session/:session/request/:request")
                .method(HttpMethod.DELETE)
                .handler(this::deletePushResponse);
        router.route(CLIENT_API_PATH + "/*").handler(routingContext -> routingContext.fail(404));

        // proxy to deployed services
        router.route().handler(this::proxy);
        service = new ServiceProxy(config);
    }

    static void error(RoutingContext ctx, int status, String msg) {
        ctx.response().setStatusCode(status).putHeader("ContentType", "text/plain").end(msg);

    }

    static Boolean isChunked(MultiMap headers) {
        List<String> te = headers.getAll("transfer-encoding");
        if (te != null) {
            boolean chunked = false;
            for (String val : te) {
                if (val.equals("chunked")) {
                    chunked = true;
                } else {
                    return null;
                }
            }
            return chunked;
        } else {
            return false;
        }
    }

    private static void sendBody(HttpServerRequest source, HttpServerResponse destination) {
        long contentLength = -1L;
        String contentLengthHeader = source.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            try {
                contentLength = Long.parseLong(contentLengthHeader);
            } catch (NumberFormatException e) {
                // Ignore ???
            }
        }
        Body body = Body.body(source, contentLength);
        long len = body.length();
        if (len >= 0) {
            destination.putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(len));
        } else {
            Boolean isChunked = DevProxyServer.isChunked(source.headers());
            destination.setChunked(len == -1 && Boolean.TRUE == isChunked);
        }
        Pipe<Buffer> pipe = body.stream().pipe();
        pipe.endOnComplete(true);
        pipe.endOnFailure(false);
        pipe.to(destination, ar -> {
            if (ar.failed()) {
                destination.reset();
            }
        });
    }

    public void proxy(RoutingContext ctx) {
        log.infov("*** entered proxy {0} {1}", ctx.request().method().toString(), ctx.request().uri());
        // Get session id from header or cookie
        String sessionId = ctx.request().getHeader(SESSION_HEADER);
        if (sessionId == null) {
            Cookie cookie = ctx.request().getCookie(SESSION_HEADER);
            if (cookie != null) {
                sessionId = cookie.getValue();
            } else {
                sessionId = GLOBAL_PROXY_SESSION;
            }
        }
        log.infov("Looking for session {0}", sessionId);

        ProxySession session = service.sessions.get(sessionId);
        if (session != null && session.running) {
            try {
                log.infov("Enqueued request for service {0} of proxy session {1}", service.config.getName(), sessionId);
                ctx.request().pause();
                session.queue.put(ctx);
            } catch (InterruptedException e) {
                DevProxyServer.error(ctx, 500, "Could not enqueue proxied request");
                log.error("Could not enqueue proxied request. Interrupted");
                return;
            }
        } else {
            service.proxy.handle(ctx.request());
        }
    }

    public void clientConnect(RoutingContext ctx) {
        // TODO: add security 401 protocol

        List<String> sessionQueryParam = ctx.queryParam("session");
        String sessionId = GLOBAL_PROXY_SESSION;
        if (!sessionQueryParam.isEmpty()) {
            sessionId = sessionQueryParam.get(0);
        }
        List<String> whoQueryParam = ctx.queryParam("who");
        String who = null;
        if (!whoQueryParam.isEmpty()) {
            who = whoQueryParam.get(0);
        }
        if (who == null) {
            ctx.response().setStatusCode(400).end();
            log.errorv("Failed Client Connect to service {0} and session {1}: who identity not sent", service.config.getName(),
                    sessionId);
            return;
        }
        synchronized (this) {
            ProxySession session = service.sessions.get(sessionId);
            if (session != null) {
                if (!who.equals(session.who)) {
                    log.errorv("Failed Client Connect for {0} to service {1} and session {2}: Existing connection {3}", who,
                            service.config.getName(), sessionId, session.who);
                    ctx.response().setStatusCode(409).putHeader("Content-Type", "text/plain").end(session.who);

                }
            } else {
                service.sessions.put(sessionId, new ProxySession(service, sessionId, who));
                ctx.response().setStatusCode(204).putHeader(POLL_LINK, CLIENT_API_PATH + "/poll/session/" + sessionId)
                        .end();
            }
        }
    }

    public void deleteClientConnection(RoutingContext ctx) {
        // TODO: add security 401 protocol

        List<String> sessionQueryParam = ctx.queryParam("session");
        String sessionId = GLOBAL_PROXY_SESSION;
        if (!sessionQueryParam.isEmpty()) {
            sessionId = sessionQueryParam.get(0);
        }
        ProxySession session = service.sessions.get(sessionId);
        if (session != null) {
            log.infov("Shutdown session {0}", sessionId);
            session.shutdown();
            ctx.response().setStatusCode(204).end();
        } else {
            ctx.response().setStatusCode(404).end();
        }
    }

    public void pushResponse(RoutingContext ctx) {
        String sessionId = ctx.pathParam("session");
        String requestId = ctx.pathParam("request");
        String kp = ctx.queryParams().get("keepAlive");
        boolean keepAlive = kp == null ? true : Boolean.parseBoolean(kp);

        ProxySession session = service.sessions.get(sessionId);
        if (session == null) {
            log.error("Push response could not find service " + service.config.getName() + " session ");
            DevProxyServer.error(ctx, 404, "Session not found for service " + service.config.getName());
            return;
        }
        RoutingContext proxiedCtx = session.dequeueResponse(requestId);
        if (proxiedCtx == null) {
            log.error("Push response could not request " + requestId + " for service " + service.config.getName() + " session "
                    + sessionId);
            ctx.response().putHeader(POLL_LINK, CLIENT_API_PATH + "/poll/session/" + sessionId);
            DevProxyServer.error(ctx, 404, "Request " + requestId + " not found");
            return;
        }
        HttpServerResponse proxiedResponse = proxiedCtx.response();
        HttpServerRequest pushedResponse = ctx.request();
        String status = pushedResponse.getHeader(STATUS_CODE_HEADER);
        if (status == null) {
            log.error("Failed to get status header");
            DevProxyServer.error(proxiedCtx, 500, "Failed");
            DevProxyServer.error(ctx, 400, "Failed to get status header");
            return;
        }
        proxiedResponse.setStatusCode(Integer.parseInt(status));
        pushedResponse.headers().forEach((key, val) -> {
            int idx = key.indexOf(HEADER_FORWARD_PREFIX);
            if (idx == 0) {
                String headerName = key.substring(HEADER_FORWARD_PREFIX.length());
                proxiedResponse.headers().add(headerName, val);
            }
        });
        sendBody(pushedResponse, proxiedResponse);
        if (keepAlive) {
            log.infov("Keep alive {0} {1}", service.config.getName(), sessionId);
            session.pollProcessing();
            executePoll(ctx, session, sessionId);
        } else {
            log.infov("End polling {0} {1}", service.config.getName(), sessionId);
            session.pollEnded();
            ctx.response().setStatusCode(204).end();
        }
    }

    public void deletePushResponse(RoutingContext ctx) {
        String sessionId = ctx.pathParam("session");
        String requestId = ctx.pathParam("request");

        ProxySession session = service.sessions.get(sessionId);
        if (session == null) {
            log.error("Delete push response could not find service " + service.config.getName() + " session ");
            DevProxyServer.error(ctx, 404, "Session not found for service " + service.config.getName());
            return;
        }
        RoutingContext proxiedCtx = session.dequeueResponse(requestId);
        if (proxiedCtx == null) {
            log.error("Delete push response could not find request " + requestId + " for service " + service.config.getName()
                    + " session " + sessionId);
            DevProxyServer.error(ctx, 404, "Request " + requestId + " not found");
            return;
        }
        proxiedCtx.fail(500);
        ctx.response().setStatusCode(204).end();
    }

    public void pollNext(RoutingContext ctx) {
        String sessionId = ctx.pathParam("session");
        log.infov("pollNext {0} {1}", service.config.getName(), sessionId);

        ProxySession session = service.sessions.get(sessionId);
        if (session == null) {
            log.error("Poll next could not find service " + service.config.getName() + " session " + sessionId);
            DevProxyServer.error(ctx, 404, "Session not found for service " + service.config.getName());
            return;
        }
        session.pollStarted();
        executePoll(ctx, session, sessionId);
    }

    private void executePoll(RoutingContext ctx, ProxySession session, String sessionId) {
        vertx.executeBlocking(new Handler<>() {

            @Override
            public void handle(Promise<Object> event) {
                final AtomicBoolean closed = new AtomicBoolean(false);
                HttpServerResponse pollResponse = ctx.response();
                pollResponse.closeHandler((v) -> closed.set(true));
                pollResponse.exceptionHandler((v) -> closed.set(true));
                ctx.request().connection().closeHandler((v) -> closed.set(true));
                ctx.request().connection().exceptionHandler((v) -> closed.set(true));
                RoutingContext proxiedCtx = null;
                try {
                    log.infov("Polling {0} {1}", service.config.getName(), sessionId);
                    proxiedCtx = session.queue.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (proxiedCtx != null) {
                        if (proxiedCtx == END_SENTINEL) {
                            log.info("Polling exiting as session no longer exists");
                            ctx.response().setStatusCode(404).end();
                            return;
                        }
                        log.infov("Got request {0} {1}", service.config.getName(), sessionId);
                        if (closed.get()) {
                            log.info("Polled message but connection was closed, returning to queue");
                            session.queue.put(proxiedCtx);
                            session.pollDisconnect();
                            return;
                        }
                    } else if (closed.get()) {
                        log.info("Client closed");
                        return;
                    } else {
                        log.info("Polled message timeout, sending 408");
                        ctx.response().setStatusCode(408).end();
                        return;
                    }
                } catch (InterruptedException e) {
                    log.error("executePoll interrupted");
                    ctx.response().setStatusCode(500).end();
                    return;
                } catch (Throwable t) {
                    log.error("executePoll failed", t);
                    ctx.response().setStatusCode(500).end();
                    return;
                }
                session.pollProcessing();
                pollResponse.setStatusCode(200);
                HttpServerRequest proxiedRequest = proxiedCtx.request();
                proxiedRequest.pause();
                proxiedRequest.headers().forEach((key, val) -> {
                    if (key.equalsIgnoreCase("Content-Length")) {
                        return;
                    }
                    pollResponse.headers().add(HEADER_FORWARD_PREFIX + key, val);
                });
                String requestId = session.queueResponse(proxiedCtx);
                pollResponse.putHeader(REQUEST_ID_HEADER, requestId);
                String responsePath = CLIENT_API_PATH + "/push/response/session/" + sessionId + "/request/"
                        + requestId;
                pollResponse.putHeader(RESPONSE_LINK, responsePath);
                pollResponse.putHeader(METHOD_HEADER, proxiedRequest.method().toString());
                pollResponse.putHeader(URI_HEADER, proxiedRequest.uri());
                sendBody(proxiedRequest, pollResponse);
            }
        }, false, null);
    }

    static final RoutingContext END_SENTINEL = new RoutingContext() {
        @Override
        public HttpServerRequest request() {
            return null;
        }

        @Override
        public HttpServerResponse response() {
            return null;
        }

        @Override
        public void next() {

        }

        @Override
        public void fail(int statusCode) {

        }

        @Override
        public void fail(Throwable throwable) {

        }

        @Override
        public void fail(int statusCode, Throwable throwable) {

        }

        @Override
        public RoutingContext put(String key, Object obj) {
            return null;
        }

        @Override
        public <T> T get(String key) {
            return null;
        }

        @Override
        public <T> T get(String key, T defaultValue) {
            return null;
        }

        @Override
        public <T> T remove(String key) {
            return null;
        }

        @Override
        public Map<String, Object> data() {
            return Map.of();
        }

        @Override
        public Vertx vertx() {
            return null;
        }

        @Override
        public String mountPoint() {
            return "";
        }

        @Override
        public Route currentRoute() {
            return null;
        }

        @Override
        public String normalizedPath() {
            return "";
        }

        @Override
        public Cookie getCookie(String name) {
            return null;
        }

        @Override
        public RoutingContext addCookie(Cookie cookie) {
            return null;
        }

        @Override
        public Cookie removeCookie(String name, boolean invalidate) {
            return null;
        }

        @Override
        public int cookieCount() {
            return 0;
        }

        @Override
        public Map<String, Cookie> cookieMap() {
            return Map.of();
        }

        @Override
        public RequestBody body() {
            return null;
        }

        @Override
        public List<FileUpload> fileUploads() {
            return List.of();
        }

        @Override
        public void cancelAndCleanupFileUploads() {

        }

        @Override
        public Session session() {
            return null;
        }

        @Override
        public boolean isSessionAccessed() {
            return false;
        }

        @Override
        public User user() {
            return null;
        }

        @Override
        public Throwable failure() {
            return null;
        }

        @Override
        public int statusCode() {
            return 0;
        }

        @Override
        public String getAcceptableContentType() {
            return "";
        }

        @Override
        public ParsedHeaderValues parsedHeaders() {
            return null;
        }

        @Override
        public int addHeadersEndHandler(Handler<Void> handler) {
            return 0;
        }

        @Override
        public boolean removeHeadersEndHandler(int handlerID) {
            return false;
        }

        @Override
        public int addBodyEndHandler(Handler<Void> handler) {
            return 0;
        }

        @Override
        public boolean removeBodyEndHandler(int handlerID) {
            return false;
        }

        @Override
        public int addEndHandler(Handler<AsyncResult<Void>> handler) {
            return 0;
        }

        @Override
        public boolean removeEndHandler(int handlerID) {
            return false;
        }

        @Override
        public boolean failed() {
            return false;
        }

        @Override
        public void setBody(Buffer body) {

        }

        @Override
        public void setSession(Session session) {

        }

        @Override
        public void setUser(User user) {

        }

        @Override
        public void clearUser() {

        }

        @Override
        public void setAcceptableContentType(String contentType) {

        }

        @Override
        public void reroute(HttpMethod method, String path) {

        }

        @Override
        public Map<String, String> pathParams() {
            return Map.of();
        }

        @Override
        public String pathParam(String name) {
            return "";
        }

        @Override
        public MultiMap queryParams() {
            return null;
        }

        @Override
        public MultiMap queryParams(Charset encoding) {
            return null;
        }

        @Override
        public List<String> queryParam(String name) {
            return List.of();
        }
    };
}
