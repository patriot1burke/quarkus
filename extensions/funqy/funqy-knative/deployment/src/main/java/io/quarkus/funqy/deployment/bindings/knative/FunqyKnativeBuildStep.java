package io.quarkus.funqy.deployment.bindings.knative;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.builder.BuildException;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExecutorBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.funqy.deployment.FunctionBuildItem;
import io.quarkus.funqy.deployment.FunctionInitializedBuildItem;
import io.quarkus.funqy.runtime.bindings.knative.FunqyKnativeBindingRecorder;
import io.quarkus.funqy.runtime.bindings.knative.FunqyKnativeConfig;
import io.quarkus.vertx.core.deployment.InternalWebVertxBuildItem;
import io.quarkus.vertx.http.deployment.DefaultRouteBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.vertx.ext.web.Route;

public class FunqyKnativeBuildStep {
    private static final Logger log = Logger.getLogger(FunqyKnativeBuildStep.class);

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
    public RootpathBuildItem staticInit(FunqyKnativeBindingRecorder binding,
            List<FunctionBuildItem> functions,
            Optional<FunctionInitializedBuildItem> hasFunctions,
            FunqyKnativeConfig knative) throws Exception {
        if (!hasFunctions.isPresent() || hasFunctions.get() == null)
            return null;

        String function = null;
        if (knative.export.isPresent()) {
            function = knative.export.get();
            boolean found = false;
            for (FunctionBuildItem funq : functions) {
                String matchName = funq.getFunctionName() == null ? funq.getMethodName() : funq.getFunctionName();
                if (function.equals(matchName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new BuildException("Cannot find function specified by quarkus.funqy.knative.export ",
                        Collections.emptyList());

            }

        } else if (functions.size() == 1) {
            function = functions.get(0).getFunctionName();
            if (function == null) {
                function = functions.get(0).getMethodName();
            }
        } else {
            throw new BuildException("Too many functions in deployment, use quarkus.funqy.knative.export to narrow it",
                    Collections.emptyList());
        }
        binding.init(function);
        return new RootpathBuildItem("/");
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    public void boot(ShutdownContextBuildItem shutdown,
            FunqyKnativeBindingRecorder binding,
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
        Consumer<Route> ut = binding.start(vertx.getVertx(),
                shutdown,
                beanContainer.getValue(),
                executorBuildItem.getExecutorProxy());

        defaultRoutes.produce(new DefaultRouteBuildItem(ut));
    }
}
