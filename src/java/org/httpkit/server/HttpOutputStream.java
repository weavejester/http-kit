package org.httpkit.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.httpkit.DynamicBytes;
import org.httpkit.DateFormatter;
import org.httpkit.HeaderMap;
import org.httpkit.HttpStatus;

public class HttpOutputStream extends OutputStream {
    private final SelectionKey key;
    private final HttpServer server;
    private final HttpStatus status;
    private final HeaderMap headers;
    private final ByteBuffer buffer;

    private enum TransferEncoding { NONE, CHUNKED, UNKNOWN }

    private boolean hasSentData = false;
    private boolean isClosed = false;
    private TransferEncoding transferEncoding;
    private long contentLength = -1;
    private long bytesTransferred = 0;

    private static final byte[] CRLF  = "\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EMPTY_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    public HttpOutputStream(SelectionKey key,
                            HttpServer server,
                            HttpStatus status,
                            HeaderMap headers,
                            int bufferSize) {
        this.key     = key;
        this.server  = server;
        this.status  = status;
        this.headers = headers;

        if (bufferSize <= 0) throw new IllegalArgumentException("Buffer size must be positive");
        this.buffer = ByteBuffer.allocate(bufferSize);

        this.contentLength    = findContentLength();
        this.transferEncoding = findTransferEncoding();
    }

    @Override
    public synchronized void close() throws IOException {
        isClosed = true;
        flushBuffer();

        // If the response is incomplete, the connection needs to be closed
        // immediately, as per RFC7230, section 3.4.
        if (contentLength >= 0 && contentLength < bytesTransferred)
            server.closeKey(key, -1);

        server.tryWrite(key, false, ByteBuffer.wrap(EMPTY_CHUNK));
    }

    @Override
    public synchronized void flush() throws IOException {
        flushBuffer();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (off + len > b.length)
            throw new IndexOutOfBoundsException("Slice exceeds array length");
        if (off < 0)
            throw new IndexOutOfBoundsException("Offset cannot be negative");

        // Don't exceed ContentLength threshold
        if (contentLength >= 0 && (bytesTransferred + len) > contentLength)
            len = (int)(contentLength - bytesTransferred);

        while (len > 0) {
            synchronized(this) {
                checkStreamOpen();
                int bytesWritten = Math.min(len, buffer.remaining());
                buffer.put(b, off, bytesWritten);
                off += bytesWritten;
                len -= bytesWritten;
                bytesTransferred += bytesWritten;
                if (!buffer.hasRemaining()) flushBuffer();
            }
        }
    }

    @Override
    public synchronized void write(int b) throws IOException {
        checkStreamOpen();
        buffer.put((byte) b);
        if (!buffer.hasRemaining()) flushBuffer();
    }

    private long findContentLength() {
        String value = getHeaderString("Content-Length");
        return value == null ? -1 : Long.parseLong(value);
    }

    private TransferEncoding findTransferEncoding() {
        if ("chunked".equalsIgnoreCase(getHeaderString("Transfer-Encoding")))
            return TransferEncoding.CHUNKED;
        else if (contentLength >= 0)
            return TransferEncoding.NONE;
        else
            return TransferEncoding.UNKNOWN;
    }

    private String getHeaderString(String headerName) {
        Object headerValue = headers.get("Transfer-Encoding");
        return (headerValue instanceof String) ? (String)headerValue : null;
    }

    private void checkStreamOpen() throws IOException {
        if (isClosed) throw new IOException("Stream closed");
    }

    private void flushBuffer() {
        buffer.flip();

        if (!hasSentData) {
            writeRequestLineAndHeaders();
            hasSentData = true;
        }

        if (buffer.hasRemaining()) {
            if (transferEncoding == TransferEncoding.CHUNKED)
                writeChunk();
            else
                server.tryWrite(key, true, buffer);
        }

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

    private void writeRequestLineAndHeaders() {
        // A Date header is required in the response (see RFC9110, Section 6.6.1)
        if (!headers.containsKey("Date"))
            headers.put("Date", DateFormatter.getDate());

        // Don't need chunked transfer encoding if the body fits entirely in the buffer.
        if (isClosed && !hasSentData && transferEncoding == TransferEncoding.UNKNOWN) {
            transferEncoding = TransferEncoding.NONE;
            headers.putOrReplace("Content-Length", String.valueOf(buffer.remaining()));
        } else {
            transferEncoding = TransferEncoding.CHUNKED;
            headers.putOrReplace("Transfer-Encoding", "chunked");
        }

        server.tryWrite(key, true, encodeRequestLineAndHeaders());
   }

    private ByteBuffer encodeRequestLineAndHeaders() {
        DynamicBytes bytes = new DynamicBytes(196);
        byte[] bs = status.getInitialLineBytes();
        bytes.append(bs, bs.length);
        headers.encodeHeaders(bytes);
        return ByteBuffer.wrap(bytes.get(), 0, bytes.length());
    }
}
