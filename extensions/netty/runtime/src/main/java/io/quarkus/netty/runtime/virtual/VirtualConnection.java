package io.quarkus.netty.runtime.virtual;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;

public class VirtualConnection {
    protected VirtualAddress address;
    protected BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    protected boolean connected = true;
    protected VirtualChannel peer;

    VirtualConnection(VirtualAddress address) {
        this.address = address;
    }

    public VirtualAddress clientAddress() {
        return address;
    }

    public BlockingQueue<Object> queue() {
        return queue;
    }

    public void close() {
        // todo more cleanup?
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public void sendMessage(Object msg) {
        peer.inboundBuffer.add(msg);
        finishPeerRead0(peer);
    }

    private void finishPeerRead0(VirtualChannel peer) {
        Future<?> peerFinishReadFuture = peer.finishReadFuture;
        if (peerFinishReadFuture != null) {
            if (!peerFinishReadFuture.isDone()) {
                runFinishPeerReadTask(peer);
                return;
            } else {
                // Lazy unset to make sure we don't prematurely unset it while scheduling a new task.
                VirtualChannel.FINISH_READ_FUTURE_UPDATER.compareAndSet(peer, peerFinishReadFuture, null);
            }
        }
        // We should only set readInProgress to false if there is any data that was read as otherwise we may miss to
        // forward data later on.
        if (peer.readInProgress && !peer.inboundBuffer.isEmpty()) {
            peer.readInProgress = false;
            peer.readInbound();
        }
    }

    private void runFinishPeerReadTask(final VirtualChannel peer) {
        // If the peer is writing, we must wait until after reads are completed for that peer before we can read. So
        // we keep track of the task, and coordinate later that our read can't happen until the peer is done.
        final Runnable finishPeerReadTask = new Runnable() {
            @Override
            public void run() {
                finishPeerRead0(peer);
            }
        };
        try {
            if (peer.writeInProgress) {
                peer.finishReadFuture = peer.eventLoop().submit(finishPeerReadTask);
            } else {
                peer.eventLoop().execute(finishPeerReadTask);
            }
        } catch (Throwable cause) {
            close();
            peer.close();
            PlatformDependent.throwException(cause);
        }
    }

    public static VirtualConnection connect(final VirtualAddress remoteAddress) {
        Channel boundChannel = VirtualChannelRegistry.get(remoteAddress);
        if (!(boundChannel instanceof VirtualServerChannel)) {
            throw new RuntimeException("Should be virtual server channel");
        }

        VirtualServerChannel serverChannel = (VirtualServerChannel) boundChannel;
        VirtualConnection conn = new VirtualConnection(remoteAddress);
        conn.peer = serverChannel.serve(conn);
        return conn;

    }
}
