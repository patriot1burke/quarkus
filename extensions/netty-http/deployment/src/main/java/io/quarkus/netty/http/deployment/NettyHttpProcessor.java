package io.quarkus.netty.http.deployment;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.netty.channel.ChannelPipeline;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.netty.deployment.NettyEventLoopsBuildItem;
import io.quarkus.netty.http.runtime.NettyHttpTemplate;
import io.quarkus.runtime.RuntimeValue;

public class NettyHttpProcessor {
    private static final Logger log = Logger.getLogger(NettyHttpProcessor.class.getName());

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void setupServer(Optional<SuppressHttpSocketBuildItem> suppress, NettyEventLoopsBuildItem executors,
            List<HttpPipelineInitializerBuildItem> initializers,
            NettyHttpTemplate http) {
        if (suppress.isPresent()) {
            return;
        }
        List<RuntimeValue<Consumer<ChannelPipeline>>> cis = initializers.stream().map(bi -> bi.getInitializer())
                .collect(Collectors.toList());
        log.info("---- setupServer - num pipelines: " + cis.size());

        if (cis.size() == 0) {
            log.info("---- setupServer - No pipelines.  Not initializing");
            return;
        }
        http.start(cis, executors.getIo());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void setupVirtual(Optional<RequireVirtualHttpBuildItem> isVirtual, NettyEventLoopsBuildItem executors,
            List<HttpPipelineInitializerBuildItem> initializers,
            NettyHttpTemplate http) {
        if (!isVirtual.isPresent()) {
            // need to have isVirtual as Optional or you get error:
            //    No producers for required item
            return;
        }
        List<RuntimeValue<Consumer<ChannelPipeline>>> cis = initializers.stream().map(bi -> bi.getInitializer())
                .collect(Collectors.toList());
        log.info("---- setupVirtual - num pipelines: " + cis.size());

        if (cis.size() == 0) {
            log.info("---- setupVirtual - No pipelines.  Not initializing");
            return;
        }

        log.info("---- virtual connection requested.");
        http.startVirtual(cis, executors.getIo());
    }

}
