package io.quarkus.netty.runtime;

import java.util.function.Supplier;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Template;

@Template
public class NettyTemplate {

    public Supplier<Object> createEventLoop(int nThreads) {
        return new Supplier<Object>() {

            volatile EventLoopGroup val;

            @Override
            public EventLoopGroup get() {
                if (val == null) {
                    synchronized (this) {
                        if (val == null) {
                            val = new NioEventLoopGroup(nThreads);
                        }
                    }
                }
                return val;
            }
        };
    }

    public RuntimeValue<EventLoopGroup> createEventLoopValue(int nThreads) {
        return new RuntimeValue<>(new NioEventLoopGroup(nThreads));
    }
}
