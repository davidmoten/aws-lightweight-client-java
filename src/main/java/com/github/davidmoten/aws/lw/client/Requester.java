package com.github.davidmoten.aws.lw.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.amazonaws.services.s3.sample.auth.AWS4SignerBase;
import com.amazonaws.services.s3.sample.auth.AWS4SignerForAuthorizationHeader;
import com.amazonaws.services.s3.sample.util.BinaryUtils;
import com.amazonaws.services.s3.sample.util.HttpUtils;

final class Requester {

    private Requester() {
        // prevent instantiation
    }

    static Builder clientAndUrl(Client client, String url) {
        return new Builder(client, url);
    }

    public static final class Builder {
        private final Client client;
        private String regionName;
        private String url;
        private HttpMethod method;
        private final Map<String, String> headers = new HashMap<>();
        private byte[] requestBody;

        private Builder(Client client, String url) {
            this.client = client;
            this.url = url;
            this.regionName = client.regionName();
        }

        public Builder2 method(HttpMethod method) {
            this.method = method;
            return new Builder2(this);
        }
    }

    public static final class Builder2 {
        private final Builder b;

        private Builder2(Builder b) {
            this.b = b;
        }

        public Builder2 header(String name, String value) {
            b.headers.put(name, value);
            return this;
        }

        public Builder2 headers(Map<String, String> headers) {
            b.headers.putAll(headers);
            return this;
        }

        public Builder2 requestBody(byte[] requestBody) {
            b.requestBody = requestBody;
            return this;
        }

        public Builder2 regionName(String regionName) {
            b.regionName = regionName;
            return this;
        }

        public byte[] execute() {
            return request(b.url, b.method.toString(), b.headers, b.requestBody,
                    b.client.serviceName(), b.regionName, b.client.accessKey(),
                    b.client.secretKey());
        }

        public String executeUtf8() {
            return new String(execute(), StandardCharsets.UTF_8);
        }

        public void executeUtf8(Consumer<String> consumer) {
            consumer.accept(executeUtf8());
        }

        public Document executeDocument() {
            try {
                DocumentBuilder f = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                return f.parse(new ByteArrayInputStream(execute()));
            } catch (ParserConfigurationException | SAXException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static byte[] request(String url, String method, Map<String, String> headers,
            byte[] requestBody, String serviceName, String regionName, String accessKey,
            String secretKey) {

        // the region-specific endpoint to the target object expressed in path style
        URL endpointUrl;
        try {
            endpointUrl = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unable to parse service endpoint: " + e.getMessage());
        }

        Map<String, String> h = new HashMap<String, String>(headers);
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

        List<Parameter> parameters = extractQueryParameters(endpointUrl);
        Map<String, String> q = parameters.stream()
                .collect(Collectors.toMap(p -> p.name, p -> p.value));

        AWS4SignerForAuthorizationHeader signer = new AWS4SignerForAuthorizationHeader(endpointUrl,
                method, serviceName, regionName);
        String authorization = signer.computeSignature(h, q, contentHashString, accessKey,
                secretKey);

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

            results.add(new Parameter(name, value));
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
    }

}
