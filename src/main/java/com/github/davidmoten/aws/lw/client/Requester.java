package com.github.davidmoten.aws.lw.client;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.github.davidmoten.aws.lw.client.internal.auth.AWS4SignerBase;
import com.github.davidmoten.aws.lw.client.internal.auth.AWS4SignerForAuthorizationHeader;
import com.github.davidmoten.aws.lw.client.internal.util.BinaryUtils;
import com.github.davidmoten.aws.lw.client.internal.util.HttpUtils;
import com.github.davidmoten.xml.XmlElement;

final class Requester {

    private Requester() {
        // prevent instantiation
    }

    static Request clientAndUrl(Client client, String url) {
        return new Request(client, url);
    }

    public static final class Request {
        private final Client client;
        private String regionName;
        private String url;
        private HttpMethod method = HttpMethod.GET;
        private final Map<String, List<String>> headers = new HashMap<>();
        private byte[] requestBody;

        private Request(Client client, String url) {
            this.client = client;
            this.url = url;
            this.regionName = client.regionName();
        }

        public Request method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Request query(String name, String value) {
            if (!url.contains("?")) {
                url += "?";
            }
            if (!url.endsWith("?")) {
                url += "&";
            }
            url += HttpUtils.urlEncode(name, false) + "=" + HttpUtils.urlEncode(value, false);
            return this;
        }

        public Request header(String name, String value) {
            put(headers, name, value);
            return this;
        }

        public Request metadata(String name, String value) {
            return header("x-amz-meta-" + name, value);
        }

        public Request requestBody(byte[] requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public Request requestBody(String requestBody) {
            this.requestBody = requestBody.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        public Request regionName(String regionName) {
            this.regionName = regionName;
            return this;
        }

        public byte[] responseAsBytes() {
            Response r = response();
            if (r.statusCode() >= 200 && r.statusCode() <= 299) {
                return r.content();
            } else {
                throw new ServiceException(r.statusCode(),
                        new String(r.content(), StandardCharsets.UTF_8));
            }
        }

        public Response response() {
            return request(url, method.toString(), combineHeaders(headers), requestBody,
                    client.serviceName(), regionName, client.credentials());
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

        public void responseAsUtf8(Consumer<String> consumer) {
            consumer.accept(responseAsUtf8());
        }

    }

    private static void put(Map<String, List<String>> map, String name, String value) {
        List<String> list = map.get(name);
        if (list == null) {
            list = new ArrayList<>();
            map.put(name, list);
        }
        list.add(value);
    }

    private static Map<String, String> combineHeaders(Map<String, List<String>> headers) {
        return headers.entrySet().stream().collect(Collectors.toMap(x -> x.getKey(),
                x -> x.getValue().stream().collect(Collectors.joining(","))));
    }

    private static Response request(String url, String method, Map<String, String> headers,
            byte[] requestBody, String serviceName, String regionName, Credentials credentials) {

        // the region-specific endpoint to the target object expressed in path style
        URL endpointUrl;
        try {
            endpointUrl = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unable to parse service endpoint: " + e.getMessage());
        }

        Map<String, String> h = new HashMap<>(headers);
        final String contentHashString;
        if (requestBody == null || requestBody.length == 0) {
            contentHashString = AWS4SignerBase.EMPTY_BODY_SHA256;
        } else {
            // compute hash of the body content
            byte[] contentHash = AWS4SignerBase.hash(requestBody);
            contentHashString = BinaryUtils.toHex(contentHash);
            h.put("content-length", "" + requestBody.length);
        }
        h.put("x-amz-content-sha256", contentHashString);
        if (credentials.sessionToken().isPresent()) {
            h.put("x-amz-security-token", credentials.sessionToken().get());
        }

        List<Parameter> parameters = extractQueryParameters(endpointUrl);
        Map<String, String> q = parameters.stream()
                .collect(Collectors.toMap(p -> p.name, p -> p.value));
        AWS4SignerForAuthorizationHeader signer = new AWS4SignerForAuthorizationHeader(endpointUrl,
                method, serviceName, regionName);
        String authorization = signer.computeSignature(h, q, contentHashString,
                credentials.accessKey(), credentials.secretKey());

        // place the computed signature into a formatted 'Authorization' header
        // and call S3
        h.put("Authorization", authorization);
        return HttpUtils.invokeHttpRequest2(endpointUrl, method, h, requestBody);
    }

    private static List<Parameter> extractQueryParameters(URL endpointUrl) {
        String query = endpointUrl.getQuery();
        if (query == null) {
            return Collections.emptyList();
        } else {
            return extractQueryParameters(query);
        }
    }

    private static final char QUERY_PARAMETER_SEPARATOR = '&';
    private static final char QUERY_PARAMETER_VALUE_SEPARATOR = '=';

    /**
     * Extract parameters from a query string, preserving encoding.
     * <p>
     * We can't use Apache HTTP Client's URLEncodedUtils.parse, mainly because we
     * don't want to decode names/values.
     *
     * @param rawQuery the query to parse
     * @return The list of parameters, in the order they were found.
     */
    private static List<Parameter> extractQueryParameters(String rawQuery) {
        List<Parameter> results = new ArrayList<>();
        int endIndex = rawQuery.length() - 1;
        int index = 0;
        while (0 <= index && index <= endIndex) {
            /*
             * Ideally we should first look for '&', then look for '=' before the '&', but
             * obviously that's not how AWS understand query parsing; see the test
             * "post-vanilla-query-nonunreserved" in the test suite. A string such as
             * "?foo&bar=qux" will be understood as one parameter with name "foo&bar" and
             * value "qux". Don't ask me why.
             */
            String name;
            String value;
            int nameValueSeparatorIndex = rawQuery.indexOf(QUERY_PARAMETER_VALUE_SEPARATOR, index);
            if (nameValueSeparatorIndex < 0) {
                // No value
                name = rawQuery.substring(index);
                value = null;

                index = endIndex + 1;
            } else {
                int parameterSeparatorIndex = rawQuery.indexOf(QUERY_PARAMETER_SEPARATOR,
                        nameValueSeparatorIndex);
                if (parameterSeparatorIndex < 0) {
                    parameterSeparatorIndex = endIndex + 1;
                }
                name = rawQuery.substring(index, nameValueSeparatorIndex);
                value = rawQuery.substring(nameValueSeparatorIndex + 1, parameterSeparatorIndex);

                index = parameterSeparatorIndex + 1;
            }
            try {
                results.add(new Parameter(URLDecoder.decode(name, "UTF-8"),
                        URLDecoder.decode(value, "UTF-8")));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        return results;
    }

    private static final class Parameter {
        private final String name;
        private final String value;

        Parameter(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("Parameter [name=");
            b.append(name);
            b.append(", value=");
            b.append(value);
            b.append("]");
            return b.toString();
        }
    }

}
