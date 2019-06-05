package io.quarkus.netty.deployment;

import io.netty.channel.EventLoopGroup;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.RuntimeValue;

public final class NettyWorkerBuildItem extends SimpleBuildItem {
    private final RuntimeValue<EventLoopGroup> eventLoop;

    public NettyWorkerBuildItem(RuntimeValue<EventLoopGroup> eventLoop) {
        this.eventLoop = eventLoop;
    }

    public RuntimeValue<EventLoopGroup> getEventLoopGroup() {
        return eventLoop;
    }
}
