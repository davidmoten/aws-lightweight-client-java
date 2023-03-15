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
    private final Queue<Object> queue = new LinkedList<>();
    private final List<String> urls = new CopyOnWriteArrayList<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    public void add(ResponseInputStream r) {
        queue.add(r);
    }

    public void add(IOException e) {
        queue.add(e);
    }

    public List<String> urls() {
        return urls;
    }

    public byte[] bytes() {
        return bytes.toByteArray();
    }

    @Override
    public synchronized ResponseInputStream request(URL endpointUrl, String httpMethod, Map<String, String> headers,
            byte[] requestBody, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        urls.add(httpMethod + ":" + endpointUrl.toString());
        Object o = queue.poll();
        if (o instanceof ResponseInputStream) {
            ResponseInputStream r = (ResponseInputStream) o;
            if (r.statusCode() == 200 && requestBody != null && httpMethod == "PUT") {
                bytes.write(requestBody);
            }
            return r;
        } else {
            throw (IOException) o;
        }
    }

}
