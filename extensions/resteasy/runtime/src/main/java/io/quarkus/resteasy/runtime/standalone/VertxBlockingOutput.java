package io.quarkus.resteasy.runtime.standalone;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import io.netty.buffer.ByteBuf;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;

public class VertxBlockingOutput implements VertxOutput {
    private static final Logger log = Logger.getLogger("io.quarkus.resteasy");

    protected boolean waitingForDrain;
    protected boolean drainHandlerRegistered;
    protected final HttpServerRequest request;
    protected boolean first = true;
    protected Throwable throwable;
    public static AtomicLong fullQueueCounter = new AtomicLong(0);

    public VertxBlockingOutput(HttpServerRequest request) {
        this.request = request;
        request.response().exceptionHandler(new Handler<Throwable>() {
            @Override
            public void handle(Throwable event) {
                throwable = event;
                log.debugf(event, "IO Exception ");
                request.connection().close();
                synchronized (request.connection()) {
                    if (waitingForDrain) {
                        request.connection().notify();
                    }
                }
            }
        });

        request.response().endHandler(new Handler<Void>() {
            @Override
            public void handle(Void event) {
                synchronized (request.connection()) {
                    if (waitingForDrain) {
                        request.connection().notify();
                    }
                }
            }
        });
    }

    Buffer createBuffer(ByteBuf data) {
        return new VertxBufferImpl(data);
    }

    @Override
    public void write(ByteBuf data, boolean last) throws IOException {
        // we are going to assume that if you start on an io thread you stay on an io thread (and vice versa)
        // if this is not the case, logic needs to change

        if (Context.isOnEventLoopThread()) {
            writeNonBlocking(data, last);
        } else {
            writeBlocking(data, last);
        }
    }

    LinkedList<Buffer> fullBuffer;
    boolean end;

    protected void writeNonBlocking(ByteBuf data, boolean last) {
        synchronized (request.connection()) {
            this.end = last;
            if (data != null) {
                if (fullBuffer == null)
                    fullBuffer = new LinkedList<>();
                fullBuffer.add(createBuffer(data));
            }
            drainBuffer(false);
        }
    }

    protected void drainBuffer(boolean isDrainThread) {
        //log.info("**** drainBuffer buffer queue size: " + (fullBuffer == null ? "0" : fullBuffer.size()));
        while (fullBuffer != null && !fullBuffer.isEmpty()) {
            //log.info("loop queue size: " + fullBuffer.size() + " on thread  " + (isDrainThread ? "drain" : "io"));
            if (request.response().writeQueueFull()) {
                fullQueueCounter.incrementAndGet();
                log.info("writeQueueFull on thread  " + (isDrainThread ? "drain" : "io"));
                if (!drainHandlerRegistered) {
                    drainHandlerRegistered = true;
                    //log.info("register drain on thread  " + (drainThread ? "drain" : "io"));
                    request.response().drainHandler(event -> {
                        //log.info("** drain triggered");
                        synchronized (request.connection()) {
                            drainBuffer(true);
                        }
                    });
                }
                return;
            }
            Buffer buf = fullBuffer.removeFirst();
            log.info("write on thread " + (isDrainThread ? "drain" : "io"));
            if (end && fullBuffer.isEmpty()) {
                request.response().end(buf);
                return;
            } else {
                request.response().write(buf);
            }
        }
        drainHandlerRegistered = false;
        if (end) {
            log.info("end on thread " + (isDrainThread ? "drain" : "io"));
            request.response().end();
        }

    }

    protected void writeBlocking(ByteBuf data, boolean last) throws IOException {
        if (last && data == null) {
            request.response().end();
            return;
        }
        //do all this in the same lock
        synchronized (request.connection()) {
            awaitWriteable();
            if (last) {
                request.response().end(createBuffer(data));
            } else {
                request.response().write(createBuffer(data));
            }
        }
    }

    private void awaitWriteable() throws IOException {
        if (first) {
            first = false;
            return;
        }
        assert Thread.holdsLock(request.connection());
        while (request.response().writeQueueFull()) {
            if (throwable != null) {
                throw new IOException(throwable);
            }
            if (Context.isOnEventLoopThread()) {
                throw new IOException("Attempting a blocking write on io thread");
            }
            if (!drainHandlerRegistered) {
                drainHandlerRegistered = true;
                request.response().drainHandler(new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        if (waitingForDrain) {
                            request.connection().notifyAll();
                        }
                    }
                });
            }
            try {
                waitingForDrain = true;
                request.connection().wait();
            } catch (InterruptedException e) {
                throw new InterruptedIOException(e.getMessage());
            } finally {
                waitingForDrain = false;
            }
        }
    }

}
