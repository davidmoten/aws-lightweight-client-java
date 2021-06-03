package com.github.davidmoten.aws.lw.client.internal.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Utilities for encoding and decoding binary data to and from different forms.
 */
public final class Util {

    private Util() {
        // prevent instantiation
    }

    public static HttpURLConnection createHttpConnection(URL endpointUrl, String httpMethod,
            Map<String, String> headers, int connectTimeoutMs, int readTimeoutMs) {
        try {
            HttpURLConnection connection = (HttpURLConnection) endpointUrl.openConnection();
            connection.setRequestMethod(httpMethod);

            if (headers != null) {
                for (Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            return connection;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String canonicalMetadataKey(String meta) {
        StringBuilder b = new StringBuilder();
        String s = meta.toLowerCase(Locale.ENGLISH);
        for (int ch : s.toCharArray()) {
            if (Character.isDigit(ch) || Character.isAlphabetic(ch)) {
                b.append((char) ch);
            }
        }
        return b.toString();
    }

    /**
     * Converts byte data to a Hex-encoded string.
     *
     * @param data data to hex encode.
     *
     * @return hex-encoded string.
     */
    public static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (int i = 0; i < data.length; i++) {
            String hex = Integer.toHexString(data[i]);
            if (hex.length() == 1) {
                // Append leading zero.
                sb.append("0");
            } else if (hex.length() == 8) {
                // Remove ff prefix from negative numbers.
                hex = hex.substring(6);
            }
            sb.append(hex);
        }
        return sb.toString().toLowerCase(Locale.getDefault());
    }

    public static URL toUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static String urlEncode(String url, boolean keepPathSlash) {
        String encoded;
        try {
            encoded = URLEncoder.encode(url, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        if (keepPathSlash) {
            return encoded.replace("%2F", "/");
        } else {
            return encoded;
        }
    }

//    /**
//     * Converts a Hex-encoded data string to the original byte data.
//     *
//     * @param hexData
//     *            hex-encoded data to decode.
//     * @return decoded data from the hex string.
//     */
//    public static byte[] fromHex(String hexData) {
//        byte[] result = new byte[(hexData.length() + 1) / 2];
//        String hexNumber = null;
//        int stringOffset = 0;
//        int byteOffset = 0;
//        while (stringOffset < hexData.length()) {
//            hexNumber = hexData.substring(stringOffset, stringOffset + 2);
//            stringOffset += 2;
//            result[byteOffset++] = (byte) Integer.parseInt(hexNumber, 16);
//        }
//        return result;
//    }
}
