package com.github.davidmoten.aws.lw.client.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.github.davidmoten.aws.lw.client.HttpClient;
import com.github.davidmoten.aws.lw.client.Response;
import com.github.davidmoten.aws.lw.client.internal.util.Util;

public final class HttpClientDefault implements HttpClient {

    public static final HttpClientDefault INSTANCE = new HttpClientDefault();

    private HttpClientDefault() {
    }

    @Override
    public Response request(URL endpointUrl, String httpMethod, Map<String, String> headers,
            byte[] requestBody, int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection connection = Util.createHttpConnection(endpointUrl, httpMethod, headers,
                connectTimeoutMs, readTimeoutMs);
        try {
            if (requestBody != null) {
                OutputStream out = connection.getOutputStream();
                out.write(requestBody);
                out.flush();
            }
            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            int responseCode = connection.getResponseCode();
            InputStream is;
            if (isOk(responseCode)) {
                is = connection.getInputStream();
            } else {
                is = connection.getErrorStream();
            }
            final byte[] bytes;
            if (is == null) {
                bytes = new byte[0];
            } else {
                bytes = readBytes(is);
            }
            return new Response(responseHeaders, bytes, responseCode);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isOk(int responseCode) {
        return responseCode >= 200 && responseCode <= 299;
    }

    private static byte[] readBytes(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while ((n = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, n);
        }
        return bytes.toByteArray();
    }

}
