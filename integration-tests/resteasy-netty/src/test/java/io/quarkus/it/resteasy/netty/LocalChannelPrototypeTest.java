package io.quarkus.it.resteasy.netty;

import java.util.concurrent.LinkedBlockingQueue;

import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.plugins.server.netty.RequestDispatcher;
import org.jboss.resteasy.plugins.server.netty.RequestHandler;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpRequestDecoder;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpResponseEncoder;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

public class LocalChannelPrototypeTest {

    private static EventLoopGroup ioLoop;
    private static ResteasyDeployment deployment = new ResteasyDeploymentImpl();
    private static RequestDispatcher dispatcher;
    private static final LocalAddress addr = new LocalAddress("netty-http-virtual");
    private static Bootstrap cb;

    @BeforeAll
    public static void setup() throws Exception {
        //EventLoopGroup ioLoop = new NioEventLoopGroup();
        EventLoopGroup eventLoop = new NioEventLoopGroup();
        ioLoop = new DefaultEventLoopGroup(4, new DefaultThreadFactory("l"));
        deployment.start();
        deployment.getRegistry().addPerRequestResource(HelloResource.class);
        dispatcher = new RequestDispatcher((SynchronousDispatcher) deployment.getDispatcher(),
                deployment.getProviderFactory(), null);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(ioLoop)
                .channel(LocalServerChannel.class)
                .handler(new ChannelInitializer<LocalServerChannel>() {
                    @Override
                    public void initChannel(LocalServerChannel ch) throws Exception {
                        //ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                    }
                })
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(new RestEasyHttpRequestDecoder(dispatcher.getDispatcher(), "",
                                RestEasyHttpRequestDecoder.Protocol.HTTP));
                        ch.pipeline().addLast(new RestEasyHttpResponseEncoder());
                        ch.pipeline().addLast(eventLoop, new RequestHandler(dispatcher));
                    }
                });

        // Start the server.
        sb.bind(addr).sync();

        cb = new Bootstrap();
        cb.group(ioLoop)
                .channel(LocalChannel.class)
                .handler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        //ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                    }
                });
    }

    @Test
    public void test() throws Exception {
        System.out.println("---- YO ----");
        Channel ch = cb.connect(addr).sync().channel();
        LinkedBlockingQueue<HttpObject> queue = new LinkedBlockingQueue<>();

        ch.pipeline().addLast(new SimpleChannelInboundHandler() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                System.out.println("******** message: " + msg.getClass().getName());
                if (msg instanceof HttpObject) {
                    queue.put((HttpObject) msg);
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                cause.printStackTrace();
                ctx.close();
            }

        });

        FullHttpRequest full = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/hello");
        ch.writeAndFlush(full).sync();

        Thread.sleep(1000);
        System.out.println("queue.size() = " + queue.size());

    }
}
