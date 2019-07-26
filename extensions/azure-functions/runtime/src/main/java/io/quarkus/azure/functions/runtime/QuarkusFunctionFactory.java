package io.quarkus.azure.functions.runtime;

import org.jboss.logging.Logger;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.Application;

public class QuarkusFunctionFactory {
    private static final Logger log = Logger.getLogger("io.quarkus.azure.functions");

    public static <T> T newInstance(Class<T> functionClass) {
        init();
        return CONTAINER.instance(functionClass);
    }

    protected static BeanContainer CONTAINER = null;

    protected static volatile boolean initialized = false;
    protected static Object lock = new Object();

    protected static void init() {
        boolean init = initialized;
        if (init)
            return;
        synchronized (lock) {
            init = initialized;
            if (init)
                return;
            if (Application.currentApplication() == null) { // were we already bootstrapped?
                try {
                    Class appClass = Class.forName("io.quarkus.runner.ApplicationImpl1");
                    String[] args = {};
                    Application app = (Application) appClass.newInstance();
                    app.start(args);

                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
            initialized = init = true;
        }
    }
}
