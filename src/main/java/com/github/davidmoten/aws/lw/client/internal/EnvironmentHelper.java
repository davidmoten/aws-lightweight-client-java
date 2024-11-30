package com.github.davidmoten.aws.lw.client.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.github.davidmoten.aws.lw.client.Credentials;
import com.github.davidmoten.aws.lw.client.HttpClient;
import com.github.davidmoten.aws.lw.client.ResponseInputStream;
import com.github.davidmoten.aws.lw.client.internal.util.Util;

public final class EnvironmentHelper {

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    private EnvironmentHelper() {
        // prevent instantiation
    }

    public static Credentials credentialsFromEnvironment(Environment env, HttpClient client) {
        // if using SnapStart we need to get the credentials from a local container
        String containerCredentialsUri = env.get("AWS_CONTAINER_CREDENTIALS_FULL_URI");
        if (containerCredentialsUri != null) {
            String containerToken = env.get("AWS_CONTAINER_AUTHORIZATION_TOKEN");
            String containerTokenFile = env.get("AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE");
            containerToken = resolveContainerToken(containerToken, containerTokenFile);
            try {
                // Create a connection to the credentials URI
                URL url = new URL(containerCredentialsUri);
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", containerToken);
                ResponseInputStream response = client.request(url, "GET", headers, null, CONNECT_TIMEOUT_MS,
                        READ_TIMEOUT_MS);

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to retrieve credentials: HTTP " + response.statusCode());
                }

                StringBuilder text = new StringBuilder();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(response))) {
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        text.append(inputLine);
                    }
                }

                // Parse the JSON response
                String json = text.toString();

                String accessKeyId = Util.jsonFieldText(json, "AccessKeyId").get();
                String secretAccessKey = Util.jsonFieldText(json, "SecretAccessKey").get();
                String sessionToken = Util.jsonFieldText(json, "Token").get();
                return new CredentialsImpl(accessKeyId, secretAccessKey, Optional.of(sessionToken));

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            String accessKey = env.get("AWS_ACCESS_KEY_ID");
            String secretKey = env.get("AWS_SECRET_ACCESS_KEY");
            Optional<String> token = Optional.ofNullable(env.get("AWS_SESSION_TOKEN"));
            return new CredentialsImpl(accessKey, secretKey, token);
        }
    }

    // VisibleForTesting
    static String resolveContainerToken(String containerToken, String containerTokenFile) {
        if (containerToken == null && containerTokenFile != null) {
            return readUtf8(containerTokenFile);
        } else if (containerToken == null) {
            throw new IllegalStateException("token not found to retrieve credentials from local container");
        } else {
            return containerToken;
        }
    }

    // VisibleForTesting
    static String readUtf8(String file) {
        try {
            byte[] bytes = Files.readAllBytes(FileSystems.getDefault().getPath(file));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read string contents from file " + file);
        }
    }
}
