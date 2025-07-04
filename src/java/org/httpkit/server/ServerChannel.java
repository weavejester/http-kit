package org.httpkit.server;

import java.nio.ByteBuffer;

public abstract class ServerChannel {
    public void tryWrite(ByteBuffer... buffers) {
        tryWrite(false, buffers);
    }

    public void tryWrite(boolean inProgress, ByteBuffer... buffers);
}
