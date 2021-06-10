package com.github.davidmoten.aws.lw.client.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.github.davidmoten.aws.lw.client.HttpClient;
import com.github.davidmoten.aws.lw.client.ResponseInputStream;
import com.github.davidmoten.aws.lw.client.internal.util.Util;

public final class HttpClientDefault implements HttpClient {

    public static final HttpClientDefault INSTANCE = new HttpClientDefault();

    private HttpClientDefault() {
    }

    @Override
    public ResponseInputStream request(URL endpointUrl, String httpMethod,
            Map<String, String> headers, byte[] requestBody, int connectTimeoutMs,
            int readTimeoutMs) throws IOException {
        HttpURLConnection connection = Util.createHttpConnection(endpointUrl, httpMethod, headers,
                connectTimeoutMs, readTimeoutMs);
        return request(connection, requestBody);
    }

    // VisibleForTesting
    static ResponseInputStream request(HttpURLConnection connection, byte[] requestBody) {
        int responseCode;
        Map<String, List<String>> responseHeaders;
        InputStream is;
        try {
            if (requestBody != null) {
                OutputStream out = connection.getOutputStream();
                out.write(requestBody);
                out.flush();
            }
            responseHeaders = connection.getHeaderFields();
            responseCode = connection.getResponseCode();
            if (isOk(responseCode)) {
                is = connection.getInputStream();
            } else {
                is = connection.getErrorStream();
            }
            if (is == null) {
                is = Util.EMPTY_INPUT_STREAM;
            }
        } catch (IOException e) {
            try {
                connection.disconnect();
            } catch (Throwable e2) {
                // ignore
            }
            throw new UncheckedIOException(e);
        }
        return new ResponseInputStream(connection, responseCode, responseHeaders, is);
    }

    private static boolean isOk(int responseCode) {
        return responseCode >= 200 && responseCode <= 299;
    }

}
