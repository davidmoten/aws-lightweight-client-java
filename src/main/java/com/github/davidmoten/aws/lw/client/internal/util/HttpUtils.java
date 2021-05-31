package com.github.davidmoten.aws.lw.client.internal.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import com.github.davidmoten.aws.lw.client.Response;
import com.github.davidmoten.aws.lw.client.ServiceException;

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

    // TODO return status code as well
    public static Response invokeHttpRequest2(URL endpointUrl, String httpMethod,
            Map<String, String> headers, byte[] requestBody) {
        HttpURLConnection connection = createHttpConnection(endpointUrl, httpMethod, headers);
        try {
            if (requestBody != null) {
                OutputStream out = connection.getOutputStream();
                out.write(requestBody);
                out.flush();
            }
        } catch (Exception e) {
            throw new RuntimeException("Request failed. " + e.getMessage(), e);
        }
        try {
            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            int responseCode = connection.getResponseCode();
            boolean ok = isOk(responseCode);
            InputStream is;
            if (ok) {
                is = connection.getInputStream();
            } else {
                is = connection.getErrorStream();
            }
            final byte[] bytes;
            if (is == null) {
                bytes = new byte[0];
            } else {
                bytes = readBytes(is);
            }
            return new Response(responseHeaders, bytes, responseCode);
        } catch (Exception e) {
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            } else {
                throw new RuntimeException("Request failed. " + e.getMessage(), e);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isOk(int responseCode) {
        return responseCode >= 200 && responseCode <= 299;
    }

    private static byte[] readBytes(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while ((n = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, n);
        }
        return bytes.toByteArray();
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
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
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
