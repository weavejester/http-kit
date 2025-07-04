package org.httpkit.server;

public class HttpAtta extends ServerAtta {
    private final LinkedList<HttpServerChannel> channelQueue;
    private boolean channelInProgress = false;

    public HttpAtta(int maxBody, int maxLine, ProxyProtocolOption proxyProtocolOption) {
        decoder = new HttpDecoder(maxBody, maxLine, proxyProtocolOption);
        channelQueue = new LinkedList<HttpServerChannel>();
    }

    public final HttpDecoder decoder;

    @Override
    public boolean isKeepAlive() {
        return keepalive || channelInProgress;
    }

    protected void tryWriteChannel(HttpServerChannel ch) {
        if (channelQueue.isEmpty()) {
            channelQueue.push(ch);
            flushChannels();
        }
        else if (ch == channelQueue.peek()) {
            flushChannels();
        } else if (!channelQueue.contains(ch)) {
            channelQueue.push(ch);
        }
    }

    // Flush all the channels until we reach one that's in progress
    private void flushChannels() {
        HttpServerChannel ch = channelQueue.peek();
        while (ch != null && !(this.channelInProgress = ch.flushBuffer())) {
            channelQueue.pop();
            ch = channelQueue.peek();
        }
    }
}
