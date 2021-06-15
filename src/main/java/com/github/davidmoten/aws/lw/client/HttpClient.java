package com.github.davidmoten.aws.lw.client;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import com.github.davidmoten.aws.lw.client.internal.HttpClientDefault;

public interface HttpClient {

    ResponseInputStream request(URL endpointUrl, String httpMethod, Map<String, String> headers,
            byte[] requestBody, int connectTimeoutMs, int readTimeoutMs) throws IOException;

    static HttpClient defaultClient() {
        return HttpClientDefault.INSTANCE;
    }
}
