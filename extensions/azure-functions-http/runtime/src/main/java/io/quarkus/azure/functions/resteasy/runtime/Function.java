package io.quarkus.azure.functions.resteasy.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import io.quarkus.netty.http.runtime.NettyHttpTemplate;
import io.quarkus.netty.runtime.virtual.VirtualConnection;
import io.quarkus.runtime.Application;

public class Function {

    public HttpResponseMessage run(
            @HttpTrigger(name = "req") HttpRequestMessage<Optional<byte[]>> request,
            final ExecutionContext context) {
        if (!started) {
            HttpResponseMessage.Builder responseBuilder = request
                    .createResponseBuilder(HttpStatus.valueOf(500)).body(
                            deploymentStatus.getBytes());
            return responseBuilder.build();
        }
        VirtualConnection connection = VirtualConnection.connect(NettyHttpTemplate.VIRTUAL_HTTP);
        try {
            return nettyDispatch(connection, request);
        } catch (Exception e) {
            e.printStackTrace();
            return request
                    .createResponseBuilder(HttpStatus.valueOf(500)).build();
        } finally {
            connection.close();
        }
    }

    static final String deploymentStatus;
    static boolean started = false;

    static {
        StringWriter error = new StringWriter();
        PrintWriter errorWriter = new PrintWriter(error, true);
        if (Application.currentApplication() == null) { // were we already bootstrapped?  Needed for mock azure unit testing.
            try {
                Class appClass = Class.forName("io.quarkus.runner.ApplicationImpl1");
                String[] args = {};
                Application app = (Application) appClass.newInstance();
                app.start(args);
                errorWriter.println("Quarkus bootstrapped successfully.");
                started = true;
            } catch (Exception ex) {
                errorWriter.println("Quarkus bootstrap failed.");
                ex.printStackTrace(errorWriter);
            }
        } else {
            errorWriter.println("Quarkus bootstrapped successfully.");
            started = true;
        }
        deploymentStatus = error.toString();
    }

    protected HttpResponseMessage nettyDispatch(VirtualConnection connection, HttpRequestMessage<Optional<byte[]>> request)
            throws Exception {

        DefaultFullHttpRequest full;
        if (request.getBody().isPresent()) {
            full = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.valueOf(request.getHttpMethod().name()),
                    request.getUri().toString(),
                    Unpooled.wrappedBuffer(request.getBody().get()));
        } else {
            full = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(request.getHttpMethod().name()),
                    request.getUri().toString());
        }
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            full.headers().set(entry.getKey(), entry.getValue());
        }
        connection.sendMessage(full);
        HttpResponseMessage.Builder responseBuilder = null;
        ByteArrayOutputStream baos = null;
        for (;;) {
            // todo should we timeout? have a timeout config?
            Object msg = connection.queue().poll(100, TimeUnit.MILLISECONDS);
            try {
                if (msg == null)
                    continue;

                if (msg instanceof HttpResponse) {
                    HttpResponse res = (HttpResponse) msg;
                    responseBuilder = request.createResponseBuilder(HttpStatus.valueOf(res.status().code()));
                    for (Map.Entry<String, String> entry : res.headers()) {
                        responseBuilder.header(entry.getKey(), entry.getValue());
                    }
                }
                if (msg instanceof HttpContent) {
                    HttpContent content = (HttpContent) msg;
                    if (baos == null) {
                        // todo what is right size?
                        baos = new ByteArrayOutputStream(500);
                    }
                    try {
                        baos.write(content.content().array());
                    } catch (IOException e) {
                        // todo better error handling
                        throw new RuntimeException("failed to write");
                    }
                }
                if (msg instanceof LastHttpContent) {
                    responseBuilder.body(baos.toByteArray());
                    return responseBuilder.build();
                }
            } finally {
                if (msg != null)
                    ReferenceCountUtil.release(msg);
            }
        }
    }
}
