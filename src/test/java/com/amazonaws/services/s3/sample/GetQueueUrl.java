package com.amazonaws.services.s3.sample;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.davidmoten.aws.lw.client.internal.Clock;
import com.github.davidmoten.aws.lw.client.internal.auth.Aws4SignerBase;
import com.github.davidmoten.aws.lw.client.internal.auth.Aws4SignerForAuthorizationHeader;
import com.github.davidmoten.aws.lw.client.internal.util.HttpUtils;
import com.github.davidmoten.aws.lw.client.internal.util.Util;

/**
 * Samples showing how to GET an object from Amazon S3 using Signature V4
 * authorization.
 */
public class GetQueueUrl {

    public static void main(String[] args) {
        getQueueUrl("amsa-xml-in", "ap-southeast-2", System.getProperty("accessKey"),
                System.getProperty("secretKey"));
    }

    /**
     * Request the content of the object '/ExampleObject.txt' from the given bucket
     * in the given region using virtual hosted-style object addressing.
     */
    public static void getQueueUrl(String queueName, String regionName, String awsAccessKey,
            String awsSecretKey) {
        System.out.println("*******************************************************");
        System.out.println("*  Executing sample 'GetQueueUrl'  *");
        System.out.println("*******************************************************");

        // the region-specific endpoint to the target object expressed in path style
        URL endpointUrl = Util.toUrl("https://sqs." + regionName
                + ".amazonaws.com/?Action=GetQueueUrl&QueueName=amsa-xml-in&Version=2012-11-05");

        // for a simple GET, we have no body so supply the precomputed 'empty' hash
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-amz-content-sha256", Aws4SignerBase.EMPTY_BODY_SHA256);

        List<Parameter> parameters = extractQueryParameters(endpointUrl.getQuery());
        Map<String, String> q = parameters.stream()
                .collect(Collectors.toMap(p -> p.name, p -> p.value));

        Aws4SignerForAuthorizationHeader signer = new Aws4SignerForAuthorizationHeader(endpointUrl,
                "GET", "sqs", regionName);
        String authorization = signer.computeSignature(Clock.DEFAULT, headers, q,
                Aws4SignerBase.EMPTY_BODY_SHA256, awsAccessKey, awsSecretKey);

        // place the computed signature into a formatted 'Authorization' header
        // and call S3
        headers.put("Authorization", authorization);
        String response = HttpUtils.invokeHttpRequest(endpointUrl, "GET", headers, null);
        System.out.println("--------- Response content ---------");
        System.out.println(response);
        System.out.println("------------------------------------");
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
