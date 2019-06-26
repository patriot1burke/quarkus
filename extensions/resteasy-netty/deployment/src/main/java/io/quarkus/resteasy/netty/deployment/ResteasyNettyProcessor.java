package io.quarkus.resteasy.netty.deployment;

import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.netty.channel.ChannelPipeline;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.netty.deployment.NettyEventLoopsBuildItem;
import io.quarkus.netty.http.deployment.HttpPipelineInitializerBuildItem;
import io.quarkus.resteasy.netty.runtime.ResteasyNettyTemplate;
import io.quarkus.resteasy.server.common.deployment.ResteasyDeploymentBuildItem;
import io.quarkus.runtime.RuntimeValue;

public class ResteasyNettyProcessor {
    private static final Logger log = Logger.getLogger(ResteasyNettyProcessor.class);

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public HttpPipelineInitializerBuildItem build(ResteasyDeploymentBuildItem deployment, NettyEventLoopsBuildItem executors,
            ResteasyNettyTemplate template) {
        RuntimeValue<Consumer<ChannelPipeline>> pipeline = template.createPipeline(deployment.getRootPath(),
                deployment.getDeployment(),
                executors.getExecutor());
        return new HttpPipelineInitializerBuildItem(pipeline);
    }
}
