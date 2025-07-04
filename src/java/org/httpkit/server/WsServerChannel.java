package org.httpkit.server;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class WsServerChannel extends ServerChannel {
    private final HttpServer server;
    private final SelectionKey key;

    public WsServerChannel(SelectionKey key, HttpServer server) {
        this.server = server;
        this.key = key;
    }

    @Override
    public void tryWrite(boolean inProgress, ByteBuffer... buffers) {
        server.tryWriter(key, buffers);
    }
}
