package com.github.davidmoten.aws.lw.client.internal.util;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

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
        } catch (Exception e) {
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

            try (BufferedReader rd = new BufferedReader(new InputStreamReader(is))) {
                String line;
                StringBuffer response = new StringBuffer();
                while ((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                return response.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException("Request failed. " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    public static HttpURLConnection createHttpConnection(URL endpointUrl, String httpMethod,
            Map<String, String> headers) {
        return createHttpConnection(endpointUrl, httpMethod, headers, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    public static HttpURLConnection createHttpConnection(URL endpointUrl, String httpMethod,
            Map<String, String> headers, int connectTimeoutMs, int readTimeoutMs) {
        try {
            HttpURLConnection connection = (HttpURLConnection) endpointUrl.openConnection();
            connection.setRequestMethod(httpMethod);

            if (headers != null) {
                for (String headerKey : headers.keySet()) {
                    connection.setRequestProperty(headerKey, headers.get(headerKey));
                }
            }

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            return connection;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create connection. " + e.getMessage(), e);
        }
    }

    public static String urlEncode(String url, boolean keepPathSlash) {
        String encoded;
        try {
            encoded = URLEncoder.encode(url, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding is not supported.", e);
        }
        if (keepPathSlash) {
            encoded = encoded.replace("%2F", "/");
        }
        return encoded;
    }
}
