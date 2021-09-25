package com.github.davidmoten.aws.lw.client;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class HttpClientTesting2 implements HttpClient {

    // needs to be volatile to work with Multipart async operations
    private Queue<ResponseInputStream> queue = new LinkedList<>();

    public void add(ResponseInputStream r) {
        queue.add(r);
    }
    
    @Override
    public synchronized ResponseInputStream request(URL endpointUrl, String httpMethod,
            Map<String, String> headers, byte[] requestBody, int connectTimeoutMs,
            int readTimeoutMs) throws IOException {
        return queue.poll();
    }

}
