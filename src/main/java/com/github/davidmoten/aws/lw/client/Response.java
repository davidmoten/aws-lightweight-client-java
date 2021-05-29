package com.github.davidmoten.aws.lw.client;

import java.util.List;
import java.util.Map;

public final class Response {
    
    private final Map<String, List<String>> headers;
    private final byte[] content;
    private final int statusCode;
    
    public Response(Map<String, List<String>> headers, byte[] content, int statusCode) {
        this.headers = headers;
        this.content = content;
        this.statusCode = statusCode;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public byte[] content() {
        return content;
    }

    public int statusCode() {
        return statusCode;
    }
    
}
