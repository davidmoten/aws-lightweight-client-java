package com.github.davidmoten.aws.lw.client;

import java.net.URL;
import java.util.Map;

import com.github.davidmoten.aws.lw.client.internal.HttpClientDefault;

public interface HttpClient {
    
    Response request(URL endpointUrl, String httpMethod, Map<String, String> headers,
            byte[] requestBody, int connectTimeoutMs, int readTimeoutMs);
    
    static HttpClient defaultClient() {
        return HttpClientDefault.INSTANCE;
    }
}
