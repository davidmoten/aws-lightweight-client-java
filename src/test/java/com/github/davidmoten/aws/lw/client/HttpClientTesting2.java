package com.github.davidmoten.aws.lw.client;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

public class HttpClientTesting2 implements HttpClient {

    // needs to be volatile to work with Multipart async operations
    private final Queue<ResponseInputStream> queue = new LinkedList<>();
    private final List<String> urls = new CopyOnWriteArrayList<>();

    public void add(ResponseInputStream r) {
        queue.add(r);
    }
    
    public List<String> urls() {
        return urls;
    }
    
    @Override
    public synchronized ResponseInputStream request(URL endpointUrl, String httpMethod,
            Map<String, String> headers, byte[] requestBody, int connectTimeoutMs,
            int readTimeoutMs) throws IOException {
        urls.add(httpMethod + ":" + endpointUrl.toString());
        return queue.poll();
    }

}
