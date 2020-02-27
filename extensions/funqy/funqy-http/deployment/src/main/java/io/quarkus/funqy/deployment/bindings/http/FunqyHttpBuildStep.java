package io.quarkus.funqy.deployment.bindings.http;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.Optional;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExecutorBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.funqy.bindings.http.FunqyHttpBindingRecorder;
import io.quarkus.funqy.deployment.FunctionInitializedBuildItem;
import io.quarkus.vertx.core.deployment.InternalWebVertxBuildItem;
import io.quarkus.vertx.http.deployment.DefaultRouteBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

public class FunqyHttpBuildStep {
    private static final Logger log = Logger.getLogger(FunqyHttpBuildStep.class);

    public static final class RootpathBuildItem extends SimpleBuildItem {

        final String deploymentRootPath;

        public RootpathBuildItem(String deploymentRootPath) {
            if (deploymentRootPath != null) {
                this.deploymentRootPath = deploymentRootPath.startsWith("/") ? deploymentRootPath : "/" + deploymentRootPath;
            } else {
                this.deploymentRootPath = null;
            }
        }

    }

    @BuildStep()
    @Record(STATIC_INIT)
    public RootpathBuildItem staticInit(FunqyHttpBindingRecorder binding,
            Optional<FunctionInitializedBuildItem> hasFunctions,
            HttpBuildTimeConfig httpConfig) throws Exception {
        if (!hasFunctions.isPresent() || hasFunctions.get() == null)
            return null;

        // The context path + the resources path
        String rootPath = httpConfig.rootPath;
        binding.init(rootPath);
        return new RootpathBuildItem(rootPath);
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    public void boot(ShutdownContextBuildItem shutdown,
            FunqyHttpBindingRecorder binding,
            BuildProducer<FeatureBuildItem> feature,
            BuildProducer<DefaultRouteBuildItem> defaultRoutes,
            BuildProducer<RouteBuildItem> routes,
            InternalWebVertxBuildItem vertx,
            RootpathBuildItem root,
            BeanContainerBuildItem beanContainer,
            ExecutorBuildItem executorBuildItem) throws Exception {

        if (root == null)
            return;
        feature.produce(new FeatureBuildItem("funq"));

        String rootPath = root.deploymentRootPath;
        boolean isDefaultOrNullDeploymentPath = rootPath.equals("/");
        if (!isDefaultOrNullDeploymentPath) {
            // We need to register a special handler for non-default deployment path (specified as application path or resteasyConfig.path)
            Handler<RoutingContext> handler = binding.vertxRequestHandler(vertx.getVertx(), beanContainer.getValue(),
                    executorBuildItem.getExecutorProxy());
            // Exact match for resources matched to the root path
            routes.produce(new RouteBuildItem(rootPath, handler, false));
            String matchPath = rootPath;
            if (matchPath.endsWith("/")) {
                matchPath += "*";
            } else {
                matchPath += "/*";
            }
            // Match paths that begin with the deployment path
            routes.produce(new RouteBuildItem(matchPath, handler, false));
        } else {
            Consumer<Route> ut = binding.start(vertx.getVertx(),
                    shutdown,
                    beanContainer.getValue(),
                    executorBuildItem.getExecutorProxy());

            defaultRoutes.produce(new DefaultRouteBuildItem(ut));
        }
    }
}
