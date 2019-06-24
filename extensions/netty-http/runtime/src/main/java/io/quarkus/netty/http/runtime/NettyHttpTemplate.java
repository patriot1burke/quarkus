package io.quarkus.netty.http.runtime;

import java.util.List;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Template;

@Template
public class NettyHttpTemplate {
    private static final Logger log = Logger.getLogger(NettyHttpTemplate.class.getName());

    public void start(List<RuntimeValue<ChannelInitializer<Channel>>> initializers, Supplier<Object> executor,
            Supplier<Object> io) {
        log.info("********* NettyHttpTemplate ");
        EventLoopGroup exLoop = (EventLoopGroup) executor.get();
        EventLoopGroup ioLoop = (EventLoopGroup) io.get();

        System.out.println("********** ex loop is null " + (exLoop == null));
        System.out.println("********** io loop is null " + (ioLoop == null));

    }
}
