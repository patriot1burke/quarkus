package io.quarkus.resteasy.netty.runtime;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.plugins.server.netty.RequestDispatcher;
import org.jboss.resteasy.plugins.server.netty.RequestHandler;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpRequestDecoder;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpResponseEncoder;
import org.jboss.resteasy.spi.ResteasyDeployment;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Template;

@Template
public class ResteasyNettyTemplate {

    public RuntimeValue<Consumer<ChannelPipeline>> createPipeline(String rootPath, ResteasyDeployment deployment,
            Supplier<Object> executorSupplier) {
        final EventLoopGroup executor = (EventLoopGroup) executorSupplier.get();
        deployment.start();
        final RequestDispatcher dispatcher = new RequestDispatcher((SynchronousDispatcher) deployment.getDispatcher(),
                deployment.getProviderFactory(), null);
        Consumer<ChannelPipeline> setup = new Consumer<ChannelPipeline>() {
            @Override
            public void accept(ChannelPipeline pipeline) {
                pipeline.addLast(new RestEasyHttpRequestDecoder(dispatcher.getDispatcher(), rootPath,
                        RestEasyHttpRequestDecoder.Protocol.HTTPS));
                pipeline.addLast(new RestEasyHttpResponseEncoder());
                pipeline.addLast(executor, new RequestHandler(dispatcher));

            }
        };
        return new RuntimeValue<>(setup);
    }

}
