package com.github.davidmoten.aws.lw.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

public class HttpClientTestingWithQueue implements HttpClient {

    // needs to be volatile to work with Multipart async operations
    private final Queue<ResponseInputStream> queue = new LinkedList<>();
    private final List<String> urls = new CopyOnWriteArrayList<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    public void add(ResponseInputStream r) {
        queue.add(r);
    }

    public List<String> urls() {
        return urls;
    }

    public byte[] bytes() {
        return bytes.toByteArray();
    }

    @Override
    public synchronized ResponseInputStream request(URL endpointUrl, String httpMethod,
            Map<String, String> headers, byte[] requestBody, int connectTimeoutMs,
            int readTimeoutMs) throws IOException {
        ResponseInputStream r = queue.poll();
        if (r.statusCode() == 200 && requestBody != null && httpMethod == "PUT") {
            bytes.write(requestBody);
        }
        urls.add(httpMethod + ":" + endpointUrl.toString());
        return r;
    }

}
