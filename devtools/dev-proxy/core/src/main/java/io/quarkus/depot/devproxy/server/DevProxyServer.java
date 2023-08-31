package io.quarkus.depot.devproxy.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.JacksonCodec;
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
        public ServiceProxy(Service service) {
            this.service = service;
            this.proxy = HttpProxy.reverseProxy(client);
            proxy.origin(service.getPort(), service.getHost());
        }

        final Service service;
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
    public static final String UPLOAD_SERVICES_API_PATH = DevProxyServer.PROXY_API_PATH + "/upload/services";
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
    Map<String, ServiceProxy> proxies = new ConcurrentHashMap<>();
    Vertx vertx;
    Router router;
    HttpClient client;

    void init() {
        client = vertx.createHttpClient();
        // API routes
        router.route(DevProxyServer.PROXY_API_PATH + "/*").handler(BodyHandler.create());
        router.route(SERVICES_API_PATH).method(HttpMethod.POST).handler(this::addService);
        router.route(SERVICES_API_PATH).method(HttpMethod.GET).handler(this::getServices);

        // CLIENT API
        router.route(CLIENT_API_PATH + "/poll/:service/session/:session").method(HttpMethod.POST).handler(this::pollNext);
        router.route(CLIENT_API_PATH + "/connect/:service").method(HttpMethod.POST).handler(this::clientConnect);
        router.route(CLIENT_API_PATH + "/connect/:service").method(HttpMethod.DELETE).handler(this::deleteClientConnection);
        router.route(CLIENT_API_PATH + "/push/response/:service/session/:session/request/:request")
                .method(HttpMethod.POST)
                .handler(this::pushResponse);
        router.route(CLIENT_API_PATH + "/push/response/:service/session/:session/request/:request")
                .method(HttpMethod.DELETE)
                .handler(this::deletePushResponse);

        // proxy to deployed services
        router.route().handler(this::proxy);
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
        log.info("*** entered proxy ***");
        List<String> dp = ctx.queryParam("_dp");
        if (dp.isEmpty()) {
            DevProxyServer.error(ctx, 404, "No proxy routing information");
            return;
        }
        String name = dp.get(0);
        ServiceProxy service = proxies.get(name);
        if (service == null) {
            DevProxyServer.error(ctx, 404, "No proxy registered for: " + name);
            return;
        }
        log.infov("Proxy to: {0}", name);

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
                log.infov("Enqueued request for service {0} of proxy session {1}", name, sessionId);
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
        String name = ctx.pathParam("service");

        List<String> sessionQueryParam = ctx.queryParam("session");
        String sessionId = GLOBAL_PROXY_SESSION;
        if (!sessionQueryParam.isEmpty()) {
            sessionId = sessionQueryParam.get(0);
        }

        ServiceProxy service = proxies.get(name);
        if (service == null) {
            log.error("Poll next could not find service " + name);
            DevProxyServer.error(ctx, 404, "Service not found: " + name);
            return;
        }
        ProxySession session = service.sessions.get(sessionId);
        if (session == null) {
            session = new ProxySession();
            log.infov("Client Connect to service {0} and session {1}", name, sessionId);
            service.sessions.put(sessionId, session);
        }
        ctx.response().setStatusCode(204).putHeader(POLL_LINK, CLIENT_API_PATH + "/poll/" + name + "/session/" + sessionId)
                .end();
    }

    public void deleteClientConnection(RoutingContext ctx) {
        // TODO: add security 401 protocol
        String name = ctx.pathParam("service");

        List<String> sessionQueryParam = ctx.queryParam("session");
        String sessionId = GLOBAL_PROXY_SESSION;
        if (!sessionQueryParam.isEmpty()) {
            sessionId = sessionQueryParam.get(0);
        }

        ServiceProxy service = proxies.get(name);
        if (service == null) {
            log.error("Poll next could not find service " + name);
            DevProxyServer.error(ctx, 404, "Service not found: " + name);
            return;
        }
        service.removeSession(sessionId);
        ctx.response().setStatusCode(204).end();
    }

    public void pushResponse(RoutingContext ctx) {
        String name = ctx.pathParam("service");
        String sessionId = ctx.pathParam("session");
        String requestId = ctx.pathParam("request");
        String kp = ctx.queryParams().get("keepAlive");
        boolean keepAlive = kp == null ? false : Boolean.parseBoolean(kp);

        ServiceProxy service = proxies.get(name);
        if (service == null) {
            log.error("Push response could not find service " + name);
            DevProxyServer.error(ctx, 404, "Service not found: " + name);
            return;
        }
        ProxySession session = service.sessions.get(sessionId);
        if (session == null) {
            log.error("Push response could not find service " + name + " session " + sessionId);
            DevProxyServer.error(ctx, 404, "Session not found: " + name + " for service " + name);
            return;
        }
        RoutingContext proxiedCtx = session.dequeueResponse(requestId);
        if (proxiedCtx == null) {
            log.error("Push response could not request " + requestId + " for service " + name + " session " + sessionId);
            ctx.response().putHeader(POLL_LINK, CLIENT_API_PATH + "/poll/" + name + "/session/" + sessionId);
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
        DevProxyServer.sendBody(pushedResponse, proxiedResponse);
        if (keepAlive) {
            log.infov("Keep alive {0} {1}", name, sessionId);
            executePoll(ctx, session, name, sessionId);
        } else {
            log.infov("End polling {0} {1}", name, sessionId);
            ctx.response().setStatusCode(204).end();
        }
    }

    public void deletePushResponse(RoutingContext ctx) {
        String name = ctx.pathParam("service");
        String sessionId = ctx.pathParam("session");
        String requestId = ctx.pathParam("request");

        ServiceProxy service = proxies.get(name);
        if (service == null) {
            log.error("Delete push response could not find service " + name);
            DevProxyServer.error(ctx, 404, "Service not found: " + name);
            return;
        }
        ProxySession session = service.sessions.get(sessionId);
        if (session == null) {
            log.error("Delete push response could not find service " + name + " session " + sessionId);
            DevProxyServer.error(ctx, 404, "Session not found: " + name + " for service " + name);
            return;
        }
        RoutingContext proxiedCtx = session.dequeueResponse(requestId);
        if (proxiedCtx == null) {
            log.error("Delete push response could not request " + requestId + " for service " + name + " session " + sessionId);
            DevProxyServer.error(ctx, 404, "Request " + requestId + " not found");
            return;
        }
        proxiedCtx.fail(500);
        ctx.response().setStatusCode(204).end();
    }


    public void pollNext(RoutingContext ctx) {
        String name = ctx.pathParam("service");
        String sessionId = ctx.pathParam("session");
        log.infov("pollNext {0} {1}", name, sessionId);

        ServiceProxy service = proxies.get(name);
        if (service == null) {
            log.error("Poll next could not find service " + name);
            DevProxyServer.error(ctx, 404, "Service not found: " + name);
            return;
        }
        ProxySession session = service.sessions.get(sessionId);
        if (session == null) {
            log.error("Poll next could not find service " + name + " session " + sessionId);
            DevProxyServer.error(ctx, 404, "Session not found: " + name + " for service " + name);
            return;
        }
        executePoll(ctx, session, name, sessionId);
    }

    private void executePoll(RoutingContext ctx, ProxySession session, String name, String sessionId) {
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
                    log.infov("Polling {0} {1}", name, sessionId);
                    proxiedCtx = session.queue.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (proxiedCtx != null) {
                        log.infov("Got request {0} {1}", name, sessionId);
                        if (closed.get()) {
                            log.info("Polled message but connection was closed, returning to queue");
                            session.queue.put(proxiedCtx);
                            return;
                        }
                    } else if (closed.get()) {
                        log.info("Polled message timeout, client closed");
                        return;
                    } else {
                        log.info("Polled message timeout, sending 408");
                        ctx.fail(408);
                        return;
                    }
                } catch (InterruptedException e) {
                    log.error("poll interrupted");
                    ctx.fail(500);
                }
                pollResponse.setStatusCode(200);
                HttpServerRequest proxiedRequest = proxiedCtx.request();
                proxiedRequest.headers().forEach((key, val) -> {
                    if (key.equalsIgnoreCase("Content-Length")) {
                        return;
                    }
                    pollResponse.headers().add(HEADER_FORWARD_PREFIX + key, val);
                });
                String requestId = session.queueResponse(proxiedCtx);
                pollResponse.putHeader(REQUEST_ID_HEADER, requestId);
                String responsePath = CLIENT_API_PATH + "/push/response/" + name + "/session/" + sessionId + "/request/"
                        + requestId;
                pollResponse.putHeader(RESPONSE_LINK, responsePath);
                pollResponse.putHeader(METHOD_HEADER, proxiedRequest.method().toString());
                pollResponse.putHeader(URI_HEADER, proxiedRequest.uri());

                DevProxyServer.sendBody(proxiedRequest, pollResponse);
            }
        }, false, null);
    }

    public void uploadServices(RoutingContext ctx) {
        JacksonCodec codec = (JacksonCodec) Json.CODEC;
        Map<String, Service> services = codec.fromBuffer(ctx.body().buffer(), new TypeReference<Map<String, Service>>() {
        });
    }

    public void addService(RoutingContext ctx) {
        Service svc = Json.decodeValue(ctx.body().buffer(), Service.class);
        registerService(svc);
        ctx.response().setStatusCode(201).end();
    }

    protected void registerService(Service svc) {
        if (proxies.containsKey(svc.getName())) {
            ServiceProxy service = proxies.get(svc.getName());
            service.proxy.origin(svc.getPort(), svc.getHost());
        } else {
            proxies.put(svc.getName(), new ServiceProxy(svc));
        }
    }

    public void getServices(RoutingContext ctx) {
        Map<String, Service> services = new HashMap<>();
        for (ServiceProxy proxy : proxies.values()) {
            services.put(proxy.service.getName(), proxy.service);
        }
        ctx.response().setStatusCode(200).putHeader("Content-Type", "application/json").end(Json.encodePrettily(services));
    }

}
