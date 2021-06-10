package com.github.davidmoten.aws.lw.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

public final class ResponseInputStream extends InputStream {

    private final HttpURLConnection connection; //nullable
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final InputStream content;

    public ResponseInputStream(HttpURLConnection connection, int statusCode,
            Map<String, List<String>> headers, InputStream content) {
        this.connection = connection;
        this.statusCode = statusCode;
        this.headers = headers;
        this.content = content;
    }

    @Override
    public int read() throws IOException {
        return content.read();
    }

    @Override
    public void close() throws IOException {
        try {
            content.close();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }
}
