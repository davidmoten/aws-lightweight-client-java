package com.github.davidmoten.aws.lw.client;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

public class HttpClientTesting2 implements HttpClient {

    // needs to be volatile to work with Multipart async operations
    public volatile ResponseInputStream responseInputStream;

    @Override
    public ResponseInputStream request(URL endpointUrl, String httpMethod,
            Map<String, String> headers, byte[] requestBody, int connectTimeoutMs,
            int readTimeoutMs) throws IOException {
        System.out.println(endpointUrl);
        return responseInputStream;
    }

}
