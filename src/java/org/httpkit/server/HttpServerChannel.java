package org.httpkit.server;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Collections;

public class HttpServerChannel extends ServerChannel {
    private final HttpServer server;
    private final SelectionKey key;
    private final LinkedList<ByteBuffer> buffer;
    private boolean inProgress;

    public HttpServerChannel(SelectionKey key, HttpServer server) {
        this.server = server;
        this.key = key;
        this.buffer = new LinkedList<ByteBuffer>();
    }

    @Override
    public void tryWrite(boolean inProgress, ByteBuffer... bs) {
        HttpAtta atta = (HttpAtta)key.attachment();
        synchronized (atta) {
            this.inProgress = inProgress;
            Collections.addAll(buffer, bs);
            atta.tryWriteChannel(this);
        }
    }

    protected boolean flushBuffer() {
        server.tryWrite(key, buffer.toArray());
        buffer.clear();
        return inProgress;
    }
}
