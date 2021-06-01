package com.github.davidmoten.aws.lw.client;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.davidmoten.xml.Preconditions;

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

    /**
     * Returns those headers that start with {@code x-amz-meta-} (and removes that
     * prefix).
     * 
     * @return headers that start with {@code x-amz-meta-} (and removes that prefix)
     */
    public Metadata metadata() {
        return new Metadata(headers //
                .entrySet() //
                .stream() //
                .filter(x -> x.getKey() != null) //
                .filter(x -> x.getKey().startsWith("x-amz-meta-")) //
                .collect(Collectors.toMap( //
                        x -> x.getKey().substring(11), //
                        x -> x.getValue().get(0))));
    }

    public Optional<String> metadata(String name) {
        Preconditions.checkNotNull(name);
        return metadata().value(name);
    }

    public byte[] content() {
        return content;
    }

    public String contentUtf8() {
        return new String(content, StandardCharsets.UTF_8);
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isOk() {
        return statusCode >= 200 && statusCode <= 299;
    }

    // TODO add toString method
}
