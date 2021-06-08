package com.github.davidmoten.aws.lw.client.internal.auth;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.github.davidmoten.aws.lw.client.internal.util.Util;

/**
 * Various Http helper routines
 */
public class HttpUtils {

    private static final int CONNECT_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 5 * 60000;

    /**
     * Makes a http request to the specified endpoint
     */
    // TODO chuck this method
    public static String invokeHttpRequest(URL endpointUrl, String httpMethod,
            Map<String, String> headers, String requestBody) {
        HttpURLConnection connection = createHttpConnection(endpointUrl, httpMethod, headers);
        try {
            if (requestBody != null) {
                try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    wr.writeBytes(requestBody);
                    wr.flush();
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException("Request failed. " + e.getMessage(), e);
        }
        return executeHttpRequest(connection);
    }

    public static String executeHttpRequest(HttpURLConnection connection) {
        try {
            // Get Response
            InputStream is;
            try {
                is = connection.getInputStream();
            } catch (IOException e) {
                is = connection.getErrorStream();
            }

            try (BufferedReader rd = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                StringBuffer response = new StringBuffer();
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                return response.toString();
            }
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException("Request failed. " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static HttpURLConnection createHttpConnection(URL endpointUrl, String httpMethod,
            Map<String, String> headers) {
        return Util.createHttpConnection(endpointUrl, httpMethod, headers, CONNECT_TIMEOUT_MS,
                READ_TIMEOUT_MS);
    }

}
