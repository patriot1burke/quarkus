package io.quarkus.netty.http.deployment;

import java.util.List;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.netty.deployment.NettyExecutorsBuildItem;
import io.quarkus.netty.http.runtime.NettyHttpTemplate;
import io.quarkus.runtime.RuntimeValue;

public class NettyHttpProcessor {
    private static final Logger log = Logger.getLogger(NettyHttpProcessor.class.getName());

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void setupServer(NettyExecutorsBuildItem executors,
            List<HttpChannelInitializerBuildItem> initializers,
            NettyHttpTemplate http) {
        log.info("********* NETTY HTTP PROCESSOR ");
        List<RuntimeValue<ChannelInitializer<Channel>>> cis = initializers.stream().map(bi -> bi.getInitializer())
                .collect(Collectors.toList());

        http.start(cis, executors.getExecutor(), executors.getIo());

    }
}
