package io.quarkus.vertx.web.runtime;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.enterprise.event.Event;

import org.jboss.logging.Logger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.netty.runtime.virtual.VirtualAddress;
import io.quarkus.netty.runtime.virtual.VirtualChannel;
import io.quarkus.netty.runtime.virtual.VirtualServerChannel;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.Timing;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.web.Route;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.Http1xServerConnection;
import io.vertx.core.http.impl.HttpHandlers;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

@Recorder
public class VertxWebRecorder {

    public static void setHotReplacement(Handler<RoutingContext> handler) {
        hotReplacementHandler = handler;
    }

    private static final Logger LOGGER = Logger.getLogger(VertxWebRecorder.class.getName());

    private static volatile Handler<RoutingContext> hotReplacementHandler;

    private static volatile Router router;
    private static volatile HttpServer server;

    public void configureRouter(RuntimeValue<Vertx> vertx, BeanContainer container, Map<String, List<Route>> routeHandlers,
            VertxHttpConfiguration vertxHttpConfiguration, LaunchMode launchMode, ShutdownContext shutdown,
            Handler<HttpServerRequest> defaultRoute, boolean isVirtual) {

        List<io.vertx.ext.web.Route> appRoutes = initializeRoutes(vertx.getValue(), routeHandlers, defaultRoute);
        if (isVirtual) {
            initializeVirtual(vertx.getValue());
        } else {
            initializeServer(vertx.getValue(), vertxHttpConfiguration, launchMode);
        }
        container.instance(RouterProducer.class).initialize(router);

        if (launchMode == LaunchMode.DEVELOPMENT) {
            shutdown.addShutdownTask(new Runnable() {
                @Override
                public void run() {
                    for (io.vertx.ext.web.Route route : appRoutes) {
                        route.remove();
                    }
                }
            });
        } else {
            shutdown.addShutdownTask(new Runnable() {
                @Override
                public void run() {
                    if (server != null)
                        server.close();
                    router = null;
                    server = null;
                    virtualBootstrap = null;
                }
            });
        }
    }

    static void initializeServer(Vertx vertx,
            VertxHttpConfiguration vertxHttpConfiguration,
            LaunchMode launchMode) {

        // Start the server
        if (server == null) {
            CountDownLatch latch = new CountDownLatch(1);
            // Http server configuration
            HttpServerOptions httpServerOptions = createHttpServerOptions(vertxHttpConfiguration, launchMode);
            Event<Object> events = Arc.container().beanManager().getEvent();
            events.select(HttpServerOptions.class).fire(httpServerOptions);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            server = vertx.createHttpServer(httpServerOptions).requestHandler(router)
                    .listen(ar -> {
                        if (ar.succeeded()) {
                            // TODO log proper message
                            Timing.setHttpServer(String.format(
                                    "Listening on: http://%s:%s", httpServerOptions.getHost(), httpServerOptions.getPort()));

                        } else {
                            // We can't throw an exception from here as we are on the event loop.
                            // We store the failure in a reference.
                            // The reference will be checked in the main thread, and the failure re-thrown.
                            failure.set(ar.cause());
                        }
                        latch.countDown();
                    });
            try {
                latch.await();
                if (failure.get() != null) {
                    throw new IllegalStateException("Unable to start the HTTP server", failure.get());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Unable to start the HTTP server", e);
            }
        }
    }

    static List<io.vertx.ext.web.Route> initializeRoutes(Vertx vertx, Map<String, List<Route>> routeHandlers,
            Handler<HttpServerRequest> defaultRoute) {
        List<io.vertx.ext.web.Route> routes = new ArrayList<>();
        if (router == null) {
            router = Router.router(vertx);
            if (hotReplacementHandler != null) {
                router.route().blockingHandler(hotReplacementHandler);
            }
        }
        for (Entry<String, List<Route>> entry : routeHandlers.entrySet()) {
            Handler<RoutingContext> handler = createHandler(entry.getKey());
            for (Route route : entry.getValue()) {
                routes.add(addRoute(router, handler, route));
            }
        }
        // Make it also possible to register the route handlers programmatically
        Event<Object> event = Arc.container().beanManager().getEvent();
        event.select(Router.class).fire(router);

        if (defaultRoute != null) {
            //TODO: can we skip the router if no other routes?
            router.route().handler(new Handler<RoutingContext>() {
                @Override
                public void handle(RoutingContext event) {
                    defaultRoute.handle(event.request());
                }
            });
        }
        return routes;
    }

    private static HttpServerOptions createHttpServerOptions(VertxHttpConfiguration vertxHttpConfiguration,
            LaunchMode launchMode) {
        // TODO other config properties
        HttpServerOptions options = new HttpServerOptions();
        options.setHost(vertxHttpConfiguration.host);
        options.setPort(vertxHttpConfiguration.determinePort(launchMode));
        return options;
    }

    private static io.vertx.ext.web.Route addRoute(Router router, Handler<RoutingContext> handler, Route routeAnnotation) {
        io.vertx.ext.web.Route route;
        if (!routeAnnotation.regex().isEmpty()) {
            route = router.routeWithRegex(routeAnnotation.regex());
        } else if (!routeAnnotation.path().isEmpty()) {
            route = router.route(routeAnnotation.path());
        } else {
            route = router.route();
        }
        if (routeAnnotation.methods().length > 0) {
            for (HttpMethod method : routeAnnotation.methods()) {
                route.method(method);
            }
        }
        if (routeAnnotation.order() != Integer.MIN_VALUE) {
            route.order(routeAnnotation.order());
        }
        if (routeAnnotation.produces().length > 0) {
            for (String produces : routeAnnotation.produces()) {
                route.produces(produces);
            }
        }
        if (routeAnnotation.consumes().length > 0) {
            for (String consumes : routeAnnotation.consumes()) {
                route.consumes(consumes);
            }
        }
        route.handler(BodyHandler.create());
        switch (routeAnnotation.type()) {
            case NORMAL:
                route.handler(handler);
                break;
            case BLOCKING:
                // We don't mind if blocking handlers are executed in parallel
                route.blockingHandler(handler, false);
                break;
            case FAILURE:
                route.failureHandler(handler);
                break;
            default:
                throw new IllegalStateException("Unsupported handler type: " + routeAnnotation.type());
        }
        LOGGER.debugf("Route registered for %s", routeAnnotation);
        return route;
    }

    @SuppressWarnings("unchecked")
    private static Handler<RoutingContext> createHandler(String handlerClassName) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = RouterProducer.class.getClassLoader();
            }
            Class<? extends Handler<RoutingContext>> handlerClazz = (Class<? extends Handler<RoutingContext>>) cl
                    .loadClass(handlerClassName);
            return handlerClazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException
                | InvocationTargetException e) {
            throw new IllegalStateException("Unable to create invoker: " + handlerClassName, e);
        }
    }

    protected static ServerBootstrap virtualBootstrap;
    public static VirtualAddress VIRTUAL_HTTP = new VirtualAddress("netty-virtual-http");

    private static void initializeVirtual(Vertx vertxRuntime) {
        if (virtualBootstrap != null)
            return;
        VertxInternal vertx = (VertxInternal) vertxRuntime;
        virtualBootstrap = new ServerBootstrap();
        HttpHandlers handlers = new HttpHandlers(
                null,
                router,
                null,
                null,
                null);

        virtualBootstrap.group(vertx.getEventLoopGroup())
                .channel(VirtualServerChannel.class)
                .handler(new ChannelInitializer<VirtualServerChannel>() {
                    @Override
                    public void initChannel(VirtualServerChannel ch) throws Exception {
                        //ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                    }
                })
                .childHandler(new ChannelInitializer<VirtualChannel>() {
                    @Override
                    public void initChannel(VirtualChannel ch) throws Exception {
                        //ContextInternal context = new EventLoopContext(vertx, ch.eventLoop(), null, null, null,
                        //        new JsonObject(), Thread.currentThread().getContextClassLoader());
                        ContextInternal context = (ContextInternal) vertx.createEventLoopContext(null, null, new JsonObject(),
                                Thread.currentThread().getContextClassLoader());
                        VertxHandler<Http1xServerConnection> handler = VertxHandler.create(context, chctx -> {
                            Http1xServerConnection conn = new Http1xServerConnection(
                                    context.owner(),
                                    null,
                                    new HttpServerOptions(),
                                    chctx,
                                    context,
                                    "localhost",
                                    handlers,
                                    null);
                            return conn;
                        });
                        ch.pipeline().addLast("handler", handler);
                    }
                });

        // Start the server.
        try {
            virtualBootstrap.bind(VIRTUAL_HTTP).sync();
        } catch (InterruptedException e) {
            throw new RuntimeException("failed to bind virtual http");
        }

    }
}
