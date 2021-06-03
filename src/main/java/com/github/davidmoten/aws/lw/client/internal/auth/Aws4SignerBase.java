package com.github.davidmoten.aws.lw.client.internal.auth;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SimpleTimeZone;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.github.davidmoten.aws.lw.client.internal.util.HttpUtils;
import com.github.davidmoten.aws.lw.client.internal.util.Util;

/**
 * Common methods and properties for all AWS4 signer variants
 */
public abstract class Aws4SignerBase {

    private static final String ALGORITHM_HMAC_SHA256 = "HmacSHA256";
    /** SHA256 hash of an empty request body **/
    public static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    public static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";

    public static final String SCHEME = "AWS4";
    public static final String ALGORITHM = "HMAC-SHA256";
    public static final String TERMINATOR = "aws4_request";

    /** format strings for the date/time and date stamps required during signing **/
    public static final String ISO8601BasicFormat = "yyyyMMdd'T'HHmmss'Z'";
    public static final String DateStringFormat = "yyyyMMdd";

    protected URL endpointUrl;
    protected String httpMethod;
    protected String serviceName;
    protected String regionName;

    protected final SimpleDateFormat dateTimeFormat;
    protected final SimpleDateFormat dateStampFormat;

    /**
     * Create a new AWS V4 signer.
     * 
     * @param endpointUrl The service endpoint, including the path to any resource.
     * @param httpMethod  The HTTP verb for the request, e.g. GET.
     * @param serviceName The signing name of the service, e.g. 's3'.
     * @param regionName  The system name of the AWS region associated with the
     *                    endpoint, e.g. us-east-1.
     */
    public Aws4SignerBase(URL endpointUrl, String httpMethod, String serviceName,
            String regionName) {
        this.endpointUrl = endpointUrl;
        this.httpMethod = httpMethod;
        this.serviceName = serviceName;
        this.regionName = regionName;

        dateTimeFormat = new SimpleDateFormat(ISO8601BasicFormat);
        dateTimeFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
        dateStampFormat = new SimpleDateFormat(DateStringFormat);
        dateStampFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    }

    /**
     * Returns the canonical collection of header names that will be included in the
     * signature. For AWS4, all header names must be included in the process in
     * sorted canonicalized order.
     */
    protected static String getCanonicalizeHeaderNames(Map<String, String> headers) {
        List<String> sortedHeaders = new ArrayList<String>();
        sortedHeaders.addAll(headers.keySet());
        Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

        StringBuilder buffer = new StringBuilder();
        for (String header : sortedHeaders) {
            if (buffer.length() > 0)
                buffer.append(";");
            buffer.append(header.toLowerCase(Locale.ENGLISH));
        }

        return buffer.toString();
    }

    /**
     * Computes the canonical headers with values for the request. For AWS4, all
     * headers must be included in the signing process.
     */
    protected static String getCanonicalizedHeaderString(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }

        // step1: sort the headers by case-insensitive order
        List<String> sortedHeaders = new ArrayList<String>();
        sortedHeaders.addAll(headers.keySet());
        Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

        // step2: form the canonical header:value entries in sorted order.
        // Multiple white spaces in the values should be compressed to a single
        // space.
        StringBuilder buffer = new StringBuilder();
        for (String key : sortedHeaders) {
            buffer.append(key.toLowerCase(Locale.ENGLISH).replaceAll("\\s+", " ") + ":"
                    + headers.get(key).replaceAll("\\s+", " "));
            buffer.append("\n");
        }

        return buffer.toString();
    }

    /**
     * Returns the canonical request string to go into the signer process; this
     * consists of several canonical sub-parts.
     * 
     * @return canonical request string
     */
    protected static String getCanonicalRequest(URL endpoint, String httpMethod,
            String queryParameters, String canonicalizedHeaderNames, String canonicalizedHeaders,
            String bodyHash) {
        return httpMethod + "\n" + getCanonicalizedResourcePath(endpoint) + "\n" + queryParameters
                + "\n" + canonicalizedHeaders + "\n" + canonicalizedHeaderNames + "\n" + bodyHash;
    }

    /**
     * Returns the canonicalized resource path for the service endpoint.
     */
    protected static String getCanonicalizedResourcePath(URL endpoint) {
        if (endpoint == null) {
            return "/";
        }
        String path = endpoint.getPath();
        if (path == null || path.isEmpty()) {
            return "/";
        }

        String encodedPath = HttpUtils.urlEncode(path, true);
        if (encodedPath.startsWith("/")) {
            return encodedPath;
        } else {
            return "/".concat(encodedPath);
        }
    }

    /**
     * Examines the specified query string parameters and returns a canonicalized
     * form.
     * <p>
     * The canonicalized query string is formed by first sorting all the query
     * string parameters, then URI encoding both the key and value and then joining
     * them, in order, separating key value pairs with an '&'.
     *
     * @param parameters The query string parameters to be canonicalized.
     *
     * @return A canonicalized form for the specified query string parameters.
     */
    protected static String getCanonicalizedQueryString(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }

        SortedMap<String, String> sorted = new TreeMap<String, String>();

        for (Entry<String, String> pair : parameters.entrySet()) {
            sorted.put(HttpUtils.urlEncode(pair.getKey(), false),
                    HttpUtils.urlEncode(pair.getValue(), false));
        }

        return sorted //
                .entrySet() //
                .stream() //
                .map(pair -> pair.getKey() + "=" + pair.getValue())
                .collect(Collectors.joining("&"));
    }

    protected static String getStringToSign(String scheme, String algorithm, String dateTime,
            String scope, String canonicalRequest) {
        return scheme + "-" + algorithm + "\n" + dateTime + "\n" + scope + "\n"
                + Util.toHex(sha256(canonicalRequest));
    }

    /**
     * Hashes the string contents (assumed to be UTF-8) using the SHA-256 algorithm.
     */
    public static byte[] sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Hashes the byte array using the SHA-256 algorithm.
     */
    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(data);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "Unable to compute sha256 hash: " + e.getMessage(), e);
        }
    }

    public static byte[] sign(String stringData, byte[] key) {
        try {
            String algorithm = ALGORITHM_HMAC_SHA256;
            byte[] data = stringData.getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Unable to calculate a request signature: " + e.getMessage(),
                    e);
        }
    }
}
