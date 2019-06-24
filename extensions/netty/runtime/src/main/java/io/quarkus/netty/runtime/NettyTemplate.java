package io.quarkus.netty.runtime;

import java.util.function.Supplier;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.quarkus.runtime.annotations.Template;

@Template
public class NettyTemplate {

    private static volatile EventLoopGroup ioGroup;
    private static volatile EventLoopGroup eventExecutor;
    private static Object lock = new Object();

    public Supplier<Object> createIoLoop(final int nThreads) {
        return new Supplier<Object>() {

            @Override
            public EventLoopGroup get() {
                return getIoLoop(nThreads);
            }
        };
    }

    public static EventLoopGroup getIoLoop(int nThreads) {
        if (ioGroup == null) {
            synchronized (lock) {
                if (ioGroup == null) {
                    ioGroup = new NioEventLoopGroup(nThreads);
                }
            }
        }
        return ioGroup;
    }

    public Supplier<Object> createEventExecutor(final int nThreads) {
        return new Supplier<Object>() {

            @Override
            public EventLoopGroup get() {
                return getEventExecutor(nThreads);
            }
        };
    }

    public static EventLoopGroup getEventExecutor(int nThreads) {
        if (eventExecutor == null) {
            synchronized (lock) {
                if (eventExecutor == null) {
                    eventExecutor = new NioEventLoopGroup(nThreads);
                }
            }
        }
        return eventExecutor;
    }
}
