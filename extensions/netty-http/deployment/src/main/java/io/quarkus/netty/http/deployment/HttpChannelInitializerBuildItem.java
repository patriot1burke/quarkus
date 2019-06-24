package io.quarkus.netty.http.deployment;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.runtime.RuntimeValue;

public class HttpChannelInitializerBuildItem extends MultiBuildItem {
    private RuntimeValue<ChannelInitializer<Channel>> initializer;

    public HttpChannelInitializerBuildItem(RuntimeValue<ChannelInitializer<Channel>> initializer) {
        this.initializer = initializer;
    }

    public RuntimeValue<ChannelInitializer<Channel>> getInitializer() {
        return initializer;
    }
}
