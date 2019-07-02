package io.quarkus.it.resteasy.netty;

import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.plugins.server.netty.RequestDispatcher;
import org.jboss.resteasy.plugins.server.netty.RequestHandler;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpRequestDecoder;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpResponseEncoder;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.junit.jupiter.api.Test;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

public class EmbeddedPrototypeTest {

    private static ResteasyDeployment deployment = new ResteasyDeploymentImpl();
    private static RequestDispatcher dispatcher;

    @Test
    public void test() throws Exception {
        EventLoopGroup eventLoop = new NioEventLoopGroup();
        deployment.start();
        deployment.getRegistry().addPerRequestResource(HelloResource.class);
        dispatcher = new RequestDispatcher((SynchronousDispatcher) deployment.getDispatcher(),
                deployment.getProviderFactory(), null);
        ChannelInitializer<Channel> childHandler = new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(new RestEasyHttpRequestDecoder(dispatcher.getDispatcher(), "",
                        RestEasyHttpRequestDecoder.Protocol.HTTP));
                ch.pipeline().addLast(new RestEasyHttpResponseEncoder());
                ch.pipeline().addLast(eventLoop, new RequestHandler(dispatcher));
            }
        };
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(childHandler);

        FullHttpRequest full = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/hello");
        embeddedChannel.writeInbound(full);

        for (;;) {
            embeddedChannel.runPendingTasks();
            Object msg = msg = embeddedChannel.outboundMessages().poll();
            if (msg != null) {
                System.out.println(msg.getClass().getName());
                if (msg instanceof LastHttpContent)
                    break;
            }
            Thread.yield();
        }
    }
}
