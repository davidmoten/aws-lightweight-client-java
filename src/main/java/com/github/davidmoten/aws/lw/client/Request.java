package com.github.davidmoten.aws.lw.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.davidmoten.aws.lw.client.internal.util.Preconditions;
import com.github.davidmoten.aws.lw.client.internal.util.Util;
import com.github.davidmoten.aws.lw.client.xml.XmlElement;

public final class Request {

    private final Client client;
    private String regionName;
    private String url;
    private HttpMethod method = HttpMethod.GET;
    private final Map<String, List<String>> headers = new HashMap<>();
    private byte[] requestBody;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private int attributeNumber = 1;
    private String attributePrefix = "Attribute";
    private String[] pathSegments;
    private final List<NameValue> queries = new ArrayList<>();

    Request(Client client, String url, String... pathSegments) {
        this.client = client;
        this.url = url;
        this.pathSegments = pathSegments;
        this.regionName = client.regionName();
        this.connectTimeoutMs = client.connectTimeoutMs();
        this.readTimeoutMs = client.readTimeoutMs();
    }

    public Request method(HttpMethod method) {
        Preconditions.checkNotNull(method);
        this.method = method;
        return this;
    }

    public Request query(String name, String value) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(value);
        queries.add(new NameValue(name, value));
        return this;
    }

    public Request attributePrefix(String attributePrefix) {
        this.attributePrefix = attributePrefix;
        this.attributeNumber = 1;
        return this;
    }

    public Request attribute(String name, String value) {
        int i = attributeNumber;
        attributeNumber++;
        return query(attributePrefix + "." + i + ".Name", name) //
                .query(attributePrefix + "." + i + ".Value", value);
    }

    public Request header(String name, String value) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(value);
        RequestHelper.put(headers, name, value);
        return this;
    }

    /**
     * Adds the header {@code x-amz-meta-KEY:value}. {@code KEY} is obtained from
     * {@code key} by converting to lower-case (headers are case-insensitive) and
     * only retaining alphabetical and digit characters.
     * 
     * @param key   metadata key
     * @param value metadata value
     * @return request builder
     */
    public Request metadata(String key, String value) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(value);
        return header("x-amz-meta-" + Util.canonicalMetadataKey(key), value);
    }

    public Request requestBody(byte[] requestBody) {
        Preconditions.checkNotNull(requestBody);
        this.requestBody = requestBody;
        return this;
    }

    public Request requestBody(String requestBody) {
        Preconditions.checkNotNull(requestBody);
        return requestBody(requestBody.getBytes(StandardCharsets.UTF_8));
    }

    public Request regionName(String regionName) {
        Preconditions.checkNotNull(regionName);
        this.regionName = regionName;
        return this;
    }

    public Request connectTimeout(long duration, TimeUnit unit) {
        Preconditions.checkArgument(duration >= 0);
        this.connectTimeoutMs = (int) unit.toMillis(duration);
        return this;
    }

    public Request readTimeout(long duration, TimeUnit unit) {
        Preconditions.checkArgument(duration >= 0);
        this.readTimeoutMs = (int) unit.toMillis(duration);
        return this;
    }

    /**
     * Opens a connection and makes the request. This method returns all the
     * response information including headers, status code, request body. If an
     * error status code is encountered (outside 200-299) then an exception is
     * <b>not</b> thrown (unlike the other methods .response*).
     * 
     * @return all response information
     */
    public Response response() {
        String u = calculateUrl(url, client.serviceName(), regionName, queries,
                Arrays.asList(pathSegments));
        return RequestHelper.request(client.clock(), client.httpClient(), u, method.toString(),
                RequestHelper.combineHeaders(headers), requestBody, client.serviceName(),
                regionName, client.credentials(), connectTimeoutMs, readTimeoutMs);
    }

    private static String calculateUrl(String url, String serviceName, String regionName,
            List<NameValue> queries, List<String> pathSegments) {
        String u = url;
        if (u == null) {
            u = "https://" //
                    + serviceName //
                    + "." //
                    + regionName //
                    + ".amazonaws.com/" //
                    + pathSegments //
                            .stream() //
                            .map(x -> trimAndRemoveLeadingAndTrailingSlashes(x)) //
                            .collect(Collectors.joining("/"));
        }
        // add queries
        for (NameValue nv : queries) {
            if (!u.contains("?")) {
                u += "?";
            }
            if (!u.endsWith("?")) {
                u += "&";
            }
            u += Util.urlEncode(nv.name, false) + "=" + Util.urlEncode(nv.value, false);
        }
        return u;
    }

    public byte[] responseAsBytes() {
        Response r = response();
        Optional<? extends RuntimeException> exception = client.exceptionFactory().create(r);
        if (!exception.isPresent()) {
            return r.content();
        } else {
            throw exception.get();
        }
    }

    public void execute() {
        responseAsBytes();
    }

    public String responseAsUtf8() {
        return new String(responseAsBytes(), StandardCharsets.UTF_8);
    }

    public XmlElement responseAsXml() {
        return XmlElement.parse(responseAsUtf8());
    }

    public String presignedUrl(long expiryDuration, TimeUnit unit) {
        String u = calculateUrl(url, client.serviceName(), regionName, queries,
                Arrays.asList(pathSegments));
        return RequestHelper.presignedUrl(client.clock(), u, method.toString(),
                RequestHelper.combineHeaders(headers), requestBody, client.serviceName(),
                regionName, client.credentials(), connectTimeoutMs, readTimeoutMs,
                unit.toSeconds(expiryDuration));
    }

    // VisibleForTesting
    static String trimAndRemoveLeadingAndTrailingSlashes(String s) {
        Preconditions.checkNotNull(s);
        s = s.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static final class NameValue {
        final String name;
        final String value;

        NameValue(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}