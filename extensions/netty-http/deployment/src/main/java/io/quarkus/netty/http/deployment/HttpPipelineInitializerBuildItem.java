package io.quarkus.netty.http.deployment;

import java.util.function.Consumer;

import io.netty.channel.ChannelPipeline;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.runtime.RuntimeValue;

public final class HttpPipelineInitializerBuildItem extends MultiBuildItem {
    private RuntimeValue<Consumer<ChannelPipeline>> initializer;

    public HttpPipelineInitializerBuildItem(RuntimeValue<Consumer<ChannelPipeline>> initializer) {
        this.initializer = initializer;
    }

    public RuntimeValue<Consumer<ChannelPipeline>> getInitializer() {
        return initializer;
    }
}
