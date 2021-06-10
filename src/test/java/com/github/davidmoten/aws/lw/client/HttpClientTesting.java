package com.github.davidmoten.aws.lw.client;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import com.github.davidmoten.aws.lw.client.internal.util.Util;

public final class HttpClientTesting implements HttpClient {
    
    public static final HttpClientTesting INSTANCE = new HttpClientTesting();

    public URL endpointUrl;
    public String httpMethod;
    public Map<String, String> headers;
    public byte[] requestBody;
    public int connectTimeoutMs;
    public int readTimeoutMs;

    @Override
    public ResponseInputStream request(URL endpointUrl, String httpMethod, Map<String, String> headers, byte[] requestBody,
            int connectTimeoutMs, int readTimeoutMs) {
        this.endpointUrl = endpointUrl;
        this.httpMethod = httpMethod;
        this.headers = headers;
        this.requestBody = requestBody;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        return new ResponseInputStream(null, 200, Collections.emptyMap(), Util.EMPTY_INPUT_STREAM);
    }

    @Override
    public String toString() {
        return "HttpClientTesting [\n  endpointUrl=" + endpointUrl + "\n  httpMethod=" + httpMethod + "\n  headers=" + headers
                + "\n  requestBody=" + new String(requestBody, StandardCharsets.UTF_8) + "\n  connectTimeoutMs="
                + connectTimeoutMs + "\n  readTimeoutMs=" + readTimeoutMs + "\n]";
    }

    public String requestBodyString() {
        return new String(requestBody, StandardCharsets.UTF_8);
    }

}
