package io.quarkus.devspace.server;

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
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.HttpProxy;

public class DevProxyServer {
    class ProxySession {
        final BlockingQueue<RoutingContext> queue = new LinkedBlockingQueue<>();
        final ConcurrentHashMap<String, RoutingContext> responsePending = new ConcurrentHashMap<>();

        volatile boolean running = true;
        AtomicLong requestId = new AtomicLong(System.currentTimeMillis());

        String queueResponse(RoutingContext ctx) {
            String requestId = Long.toString(this.requestId.incrementAndGet());
            responsePending.put(requestId, ctx);
            return requestId;
        }

        RoutingContext dequeueResponse(String requestId) {
            return responsePending.remove(requestId);
        }

        void shutdown() {
            running = false;
            while (!queue.isEmpty()) {
                List<RoutingContext> requests = new ArrayList<>();
                queue.drainTo(requests);
                requests.stream().forEach((ctx) -> ctx.fail(500));
            }
        }

    }

    class ServiceProxy {
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

        void removeSession(String name) {
            // forward RoutingContext to proxy
            ProxySession session = sessions.remove(name);
            if (session != null) {
                session.running = false;
                while (!session.queue.isEmpty()) {
                    List<RoutingContext> requests = new ArrayList<>();
                    session.queue.drainTo(requests);
                    requests.stream().forEach((ctx) -> proxy.handle(ctx.request()));
                }
            }
        }
    }

    public static final String CLIENT_API_PATH = "/_dev_proxy_client_";
    public static final String SERVICES_API_PATH = DevProxyServer.PROXY_API_PATH + "/services";
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

    public static final long POLL_TIMEOUT = 1000;
    protected static final Logger log = Logger.getLogger(DevProxyServer.class);
    protected ServiceProxy service;
    protected Vertx vertx;
    protected Router router;
    protected HttpClient client;

    protected void init(ServiceConfig config) {
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

    public Vertx getVertx() {
        return vertx;
    }

    public void setVertx(Vertx vertx) {
        this.vertx = vertx;
    }

    public Router getRouter() {
        return router;
    }

    public void setRouter(Router router) {
        this.router = router;
    }

    public void proxy(RoutingContext ctx) {
        log.debug("*** entered proxy ***");
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
        log.debugv("Looking for session {0}", sessionId);

        ProxySession session = service.sessions.get(sessionId);
        if (session != null && session.running) {
            try {
                log.debugv("Enqueued request for service {0} of proxy session {1}", service.config.getName(), sessionId);
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

        ProxySession session = service.sessions.get(sessionId);
        if (session == null) {
            session = new ProxySession();
            log.debugv("Client Connect to service {0} and session {1}", service.config.getName(), sessionId);
            service.sessions.put(sessionId, session);
        }
        ctx.response().setStatusCode(204).putHeader(POLL_LINK, CLIENT_API_PATH + "/poll/session/" + sessionId)
                .end();
    }

    public void deleteClientConnection(RoutingContext ctx) {
        // TODO: add security 401 protocol

        List<String> sessionQueryParam = ctx.queryParam("session");
        String sessionId = GLOBAL_PROXY_SESSION;
        if (!sessionQueryParam.isEmpty()) {
            sessionId = sessionQueryParam.get(0);
        }

        service.removeSession(sessionId);
        ctx.response().setStatusCode(204).end();
    }

    public void pushResponse(RoutingContext ctx) {
        String sessionId = ctx.pathParam("session");
        String requestId = ctx.pathParam("request");
        String kp = ctx.queryParams().get("keepAlive");
        boolean keepAlive = kp == null ? false : Boolean.parseBoolean(kp);

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
            log.debugv("Keep alive {0} {1}", service.config.getName(), sessionId);
            executePoll(ctx, session, sessionId);
        } else {
            log.debugv("End polling {0} {1}", service.config.getName(), sessionId);
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
        log.debugv("pollNext {0} {1}", service.config.getName(), sessionId);

        ProxySession session = service.sessions.get(sessionId);
        if (session == null) {
            log.error("Poll next could not find service " + service.config.getName() + " session " + sessionId);
            DevProxyServer.error(ctx, 404, "Session not found for service " + service.config.getName());
            return;
        }
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
                    log.debugv("Polling {0} {1}", service.config.getName(), sessionId);
                    proxiedCtx = session.queue.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (proxiedCtx != null) {
                        log.debugv("Got request {0} {1}", service.config.getName(), sessionId);
                        if (closed.get()) {
                            log.debug("Polled message but connection was closed, returning to queue");
                            session.queue.put(proxiedCtx);
                            return;
                        }
                    } else if (closed.get()) {
                        log.debug("Polled message timeout, client closed");
                        return;
                    } else {
                        log.debug("Polled message timeout, sending 408");
                        ctx.fail(408);
                        return;
                    }
                } catch (InterruptedException e) {
                    log.error("poll interrupted");
                    ctx.fail(500);
                }
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
}
