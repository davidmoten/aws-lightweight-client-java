package com.github.davidmoten.aws.lw.client;

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

    public Map<String, List<String>> metadata() {
        return headers //
                .entrySet() //
                .stream() //
                .filter(x -> x.getKey() != null) //
                .filter(x -> x.getKey().startsWith("x-amz-meta-")) //
                .collect(Collectors.toMap( //
                        x -> x.getKey().substring(11), //
                        x -> x.getValue()));
    }

    public Optional<String> metadataFirst(String name) {
        Preconditions.checkNotNull(name);
        List<String> list = metadata().get(name);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(list.get(0));
        }
    }

    public byte[] content() {
        return content;
    }

    public int statusCode() {
        return statusCode;
    }

}
