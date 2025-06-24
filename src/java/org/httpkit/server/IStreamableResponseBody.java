package org.httpkit.server;

import java.io.OutputStream;

public interface IStreamableResponseBody {
    void run(OutputStream outputStream);
}
