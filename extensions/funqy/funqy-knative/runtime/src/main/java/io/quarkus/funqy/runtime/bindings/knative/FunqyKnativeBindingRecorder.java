package io.quarkus.funqy.runtime.bindings.knative;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.funqy.runtime.FunctionConstructor;
import io.quarkus.funqy.runtime.FunctionInvoker;
import io.quarkus.funqy.runtime.FunctionRecorder;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

/**
 * Provides the runtime methods to bootstrap Quarkus Funq
 */
@Recorder
public class FunqyKnativeBindingRecorder {
    private static ObjectMapper objectMapper;
    private static FunctionInvoker invoker;

    public void init(String function) {
        objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        invoker = FunctionRecorder.registry.matchInvoker(function);
        if (invoker.hasInput()) {
            ObjectReader reader = objectMapper.readerFor(invoker.getInputType());
            invoker.getBindingContext().put(ObjectReader.class.getName(), reader);
        }
        if (invoker.hasOutput()) {
            ObjectWriter writer = objectMapper.writerFor(invoker.getOutputType());
            invoker.getBindingContext().put(ObjectWriter.class.getName(), writer);
        }
    }

    public Consumer<Route> start(RuntimeValue<Vertx> vertx,
            ShutdownContext shutdown,
            BeanContainer beanContainer,
            Executor executor) {

        shutdown.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                FunctionConstructor.CONTAINER = null;
                FunctionRecorder.registry = null;
                invoker = null;
                objectMapper = null;
            }
        });
        FunctionConstructor.CONTAINER = beanContainer;

        Handler<RoutingContext> handler = vertxRequestHandler(vertx, beanContainer, executor);

        return new Consumer<Route>() {

            @Override
            public void accept(Route route) {
                route.handler(handler);
            }
        };
    }

    public Handler<RoutingContext> vertxRequestHandler(RuntimeValue<Vertx> vertx,
            BeanContainer beanContainer, Executor executor) {
        return new VertxRequestHandler(vertx.getValue(), beanContainer, invoker, objectMapper, executor);
    }

}
