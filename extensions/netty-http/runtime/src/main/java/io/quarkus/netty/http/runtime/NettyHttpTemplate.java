package io.quarkus.netty.http.runtime;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.jboss.logging.Logger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.quarkus.netty.runtime.virtual.VirtualAddress;
import io.quarkus.netty.runtime.virtual.VirtualChannel;
import io.quarkus.netty.runtime.virtual.VirtualServerChannel;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Template;

@Template
public class NettyHttpTemplate {
    private static final Logger log = Logger.getLogger(NettyHttpTemplate.class.getName());
    protected static ServerBootstrap bootstrap;
    protected static ServerBootstrap virtualBootstrap;
    protected static String hostname = null;
    protected static int configuredPort = 8080;
    public static int runtimePort = -1;
    protected String root = "";
    private static SSLContext sslContext;
    private static int maxRequestSize = 1024 * 1024 * 10;
    private static int maxInitialLineLength = 4096;
    private static int maxHeaderSize = 8192;
    private static int maxChunkSize = 8192;
    private static int backlog = 128;
    private int ioWorkerCount = Runtime.getRuntime().availableProcessors() * 2;
    // default no idle timeout.
    private static int idleTimeout = -1;
    private static Map<ChannelOption, Object> channelOptions = Collections.emptyMap();

    public static VirtualAddress VIRTUAL_HTTP = new VirtualAddress("netty-virtual-http");

    public void startVirtual(List<RuntimeValue<Consumer<ChannelPipeline>>> initializers, Supplier<Object> io) {
        EventLoopGroup ioLoop = (EventLoopGroup) io.get();
        final List<Consumer<ChannelPipeline>> list = initializers.stream().map(rv -> rv.getValue())
                .collect(Collectors.toList());
        virtualBootstrap = new ServerBootstrap();
        virtualBootstrap.group(ioLoop)
                .channel(VirtualServerChannel.class)
                .handler(new ChannelInitializer<VirtualServerChannel>() {
                    @Override
                    public void initChannel(VirtualServerChannel ch) throws Exception {
                        //ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                    }
                })
                .childHandler(new ChannelInitializer<VirtualChannel>() {
                    @Override
                    public void initChannel(VirtualChannel ch) throws Exception {
                        ChannelPipeline channelPipeline = ch.pipeline();
                        for (Consumer<ChannelPipeline> ci : list) {
                            ci.accept(channelPipeline);
                        }
                    }
                });

        // Start the server.
        try {
            virtualBootstrap.bind(VIRTUAL_HTTP).sync();
        } catch (InterruptedException e) {
            throw new RuntimeException("failed to bind virtual http");
        }
    }

    public void start(List<RuntimeValue<Consumer<ChannelPipeline>>> initializers, Supplier<Object> io) {
        EventLoopGroup ioLoop = (EventLoopGroup) io.get();

        bootstrap = new ServerBootstrap();
        bootstrap.group(ioLoop)
                .channel(NioServerSocketChannel.class)
                .childHandler(createChannelInitializer(initializers))
                .option(ChannelOption.SO_BACKLOG, backlog)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        for (Map.Entry<ChannelOption, Object> entry : channelOptions.entrySet()) {
            bootstrap.option(entry.getKey(), entry.getValue());
        }

        final InetSocketAddress socketAddress;
        if (null == hostname || hostname.isEmpty()) {
            socketAddress = new InetSocketAddress(configuredPort);
        } else {
            socketAddress = new InetSocketAddress(hostname, configuredPort);
        }

        Channel channel = bootstrap.bind(socketAddress).syncUninterruptibly().channel();
        runtimePort = ((InetSocketAddress) channel.localAddress()).getPort();
        log.info("********* NettyHttpTemplate runtimePort: " + runtimePort);
    }

    private ChannelInitializer<SocketChannel> createChannelInitializer(
            List<RuntimeValue<Consumer<ChannelPipeline>>> initializers) {
        final List<Consumer<ChannelPipeline>> list = initializers.stream().map(rv -> rv.getValue())
                .collect(Collectors.toList());
        log.info("********* NettyHttpTemplate createChannelInitializer size: " + initializers.size());
        if (sslContext == null) {
            log.info("********* NettyHttpTemplate createChannelInitializer no ssl");
            return new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    setupHandlers(ch, list);
                }
            };
        } else {
            return new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    SSLEngine engine = sslContext.createSSLEngine();
                    engine.setUseClientMode(false);
                    ch.pipeline().addFirst(new SslHandler(engine));
                    setupHandlers(ch, list);
                }
            };
        }
    }

    private static void setupHandlers(SocketChannel ch, List<Consumer<ChannelPipeline>> initializers) {
        log.info("********* NettyHttpTemplate setupHandlers initializer size: " + initializers.size());
        ChannelPipeline channelPipeline = ch.pipeline();
        channelPipeline.addLast(new HttpRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize));
        channelPipeline.addLast(new HttpResponseEncoder());
        channelPipeline.addLast(new HttpObjectAggregator(maxRequestSize));
        if (idleTimeout > 0) {
            channelPipeline.addLast("idleStateHandler", new IdleStateHandler(0, 0, idleTimeout));
        }
        for (Consumer<ChannelPipeline> ci : initializers) {
            ci.accept(channelPipeline);
        }
    }
}
