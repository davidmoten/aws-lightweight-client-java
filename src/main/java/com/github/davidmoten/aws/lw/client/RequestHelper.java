package com.github.davidmoten.aws.lw.client;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.davidmoten.aws.lw.client.internal.Clock;
import com.github.davidmoten.aws.lw.client.internal.auth.Aws4SignerBase;
import com.github.davidmoten.aws.lw.client.internal.auth.Aws4SignerForAuthorizationHeader;
import com.github.davidmoten.aws.lw.client.internal.auth.Aws4SignerForQueryParameterAuth;
import com.github.davidmoten.aws.lw.client.internal.util.Preconditions;
import com.github.davidmoten.aws.lw.client.internal.util.Util;

final class RequestHelper {

    private RequestHelper() {
        // prevent instantiation
    }

    static void put(Map<String, List<String>> map, String name, String value) {
        Preconditions.checkNotNull(map);
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(value);
        List<String> list = map.get(name);
        if (list == null) {
            list = new ArrayList<>();
            map.put(name, list);
        }
        list.add(value);
    }

    static Map<String, String> combineHeaders(Map<String, List<String>> headers) {
        Preconditions.checkNotNull(headers);
        return headers.entrySet().stream().collect(Collectors.toMap(x -> x.getKey(),
                x -> x.getValue().stream().collect(Collectors.joining(","))));
    }

    static String presignedUrl(Clock clock, String url, String method, Map<String, String> headers,
            byte[] requestBody, String serviceName, String regionName, Credentials credentials,
            int connectTimeoutMs, int readTimeoutMs, long expirySeconds) {

        // the region-specific endpoint to the target object expressed in path style
        URL endpointUrl = Util.toUrl(url);

        Map<String, String> h = new HashMap<>(headers);
        final String contentHashString;
        if (isEmpty(requestBody)) {
            contentHashString = Aws4SignerBase.UNSIGNED_PAYLOAD;
            h.put("x-amz-content-sha256", "");
        } else {
            // compute hash of the body content
            byte[] contentHash = Util.sha256(requestBody);
            contentHashString = Util.toHex(contentHash);
            h.put("content-length", "" + requestBody.length);
            h.put("x-amz-content-sha256", contentHashString);
        }

//        if (credentials.sessionToken().isPresent()) {
//            h.put("x-amz-security-token", credentials.sessionToken().get());
//        }

        List<Parameter> parameters = extractQueryParameters(endpointUrl);
        Map<String, String> q = parameters.stream()
                .collect(Collectors.toMap(p -> p.name, p -> p.value));
        // construct the query parameter string to accompany the url

        // for SignatureV4, the max expiry for a presigned url is 7 days,
        // expressed in seconds
        q.put("X-Amz-Expires", "" + expirySeconds);

        Aws4SignerForQueryParameterAuth signer = new Aws4SignerForQueryParameterAuth(endpointUrl,
                method, serviceName, regionName);
        String authorizationQueryParameters = signer.computeSignature(clock, h, q,
                contentHashString, credentials.accessKey(), credentials.secretKey());

        // build the presigned url to incorporate the authorization elements as query
        // parameters
        String u = endpointUrl.toString();
        final String presignedUrl;
        if (u.contains("?")) {
            presignedUrl = u + "&" + authorizationQueryParameters;
        } else {
            presignedUrl = u + "?" + authorizationQueryParameters;
        }
        return presignedUrl;
    }

    static Response request(Clock clock, HttpClient httpClient, String url, String method,
            Map<String, String> headers, byte[] requestBody, String serviceName, String regionName,
            Credentials credentials, int connectTimeoutMs, int readTimeoutMs) {

        // the region-specific endpoint to the target object expressed in path style
        URL endpointUrl = Util.toUrl(url);

        Map<String, String> h = new HashMap<>(headers);
        final String contentHashString;
        if (isEmpty(requestBody)) {
            contentHashString = Aws4SignerBase.EMPTY_BODY_SHA256;
        } else {
            // compute hash of the body content
            byte[] contentHash = Util.sha256(requestBody);
            contentHashString = Util.toHex(contentHash);
            h.put("content-length", "" + requestBody.length);
        }
        h.put("x-amz-content-sha256", contentHashString);
        if (credentials.sessionToken().isPresent()) {
            h.put("x-amz-security-token", credentials.sessionToken().get());
        }

        List<Parameter> parameters = extractQueryParameters(endpointUrl);
        Map<String, String> q = parameters.stream()
                .collect(Collectors.toMap(p -> p.name, p -> p.value));
        Aws4SignerForAuthorizationHeader signer = new Aws4SignerForAuthorizationHeader(endpointUrl,
                method, serviceName, regionName);
        String authorization = signer.computeSignature(clock, h, q, contentHashString,
                credentials.accessKey(), credentials.secretKey());

        // place the computed signature into a formatted 'Authorization' header
        // and call S3
        h.put("Authorization", authorization);
        return httpClient.request(endpointUrl, method, h, requestBody, connectTimeoutMs,
                readTimeoutMs);
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
    // VisibleForTesting
    static List<Parameter> extractQueryParameters(String rawQuery) {
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
            if (value != null) {
                try {
                    results.add(new Parameter(URLDecoder.decode(name, "UTF-8"),
                            URLDecoder.decode(value, "UTF-8")));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return results;
    }

    // VisibleForTesting
    static final class Parameter {
        final String name;
        final String value;

        Parameter(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    static boolean isEmpty(byte[] array) {
        return array == null || array.length == 0;
    }

}
