package org.httpkit.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class HttpOutputStream extends OutputStream {
    private final SelectionKey key;
    private final HttpServer server;
    private final ByteBuffer buffer;
    private final boolean isChunked;
    private boolean isClosed = false;

    private static final byte[] CRLF  = "\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EMPTY_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    public HttpOutputStream(SelectionKey key, HttpServer server, boolean isChunked, int bufSize) {
        this.key = key;
        this.server = server;
        this.isChunked = isChunked;
        if (bufSize <= 0) throw new IllegalArgumentException("Buffer size must be positive");
        this.buffer = ByteBuffer.allocate(bufSize);
    }

    public synchronized void close() throws IOException {
        isClosed = true;
        if (buffer.position() > 0) flushBuffer();
        server.tryWrite(key, false, ByteBuffer.wrap(EMPTY_CHUNK));
    }

    public synchronized void flush() throws IOException {
        flushBuffer();
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (off + len > b.length)
            throw new IndexOutOfBoundsException("Slice exceeds array length");
        if (off < 0)
            throw new IndexOutOfBoundsException("Offset cannot be negative");

        while (len > 0) {
            synchronized(this) {
                checkStreamOpen();
                int bytesWritten = Math.min(len, buffer.remaining());
                buffer.put(b, off, bytesWritten);
                off += bytesWritten;
                len -= bytesWritten;
                if (!buffer.hasRemaining()) flushBuffer();
            }
        }
    }

    public synchronized void write(int b) throws IOException {
        checkStreamOpen();
        buffer.put((byte) b);
        if (!buffer.hasRemaining()) flushBuffer();
    }

    private void checkStreamOpen() throws IOException {
        if (isClosed) throw new IOException("Stream closed");
    }

    private void flushBuffer() {
        buffer.flip();
        if (isChunked) { writeChunk(); }
        else           { server.tryWrite(key, true, buffer); }
        buffer.clear();
    }

    private void writeChunk() {
        server.tryWrite(key, true,
                        ByteBuffer.wrap(chunkSizeBytes(buffer.remaining())),
                        buffer,
                        ByteBuffer.wrap(CRLF));
    }

    private byte[] chunkSizeBytes(int size) {
        String chunkSize = Integer.toHexString(size) + "\r\n";
        return chunkSize.getBytes(StandardCharsets.US_ASCII);
    }
}
