package com.github.davidmoten.aws.lw.client.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Optional;

import com.github.davidmoten.aws.lw.client.Credentials;
import com.github.davidmoten.aws.lw.client.internal.util.Util;

public class EnvironmentHelper {

    public static Credentials credentialsFromEnvironment(Environment env) {
        // if using SnapStart we need to get the credentials from a local container
        String containerCredentialsUri = env.get("AWS_CONTAINER_CREDENTIALS_FULL_URI");
        if (containerCredentialsUri != null) {
            String containerToken = env.get("AWS_CONTAINER_AUTHORIZATION_TOKEN");
            String containerTokenFile = env.get("AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE");
            if (containerToken == null && containerTokenFile != null) {
                containerToken = readUtf8(containerTokenFile);
            }
            try {
                // Create a connection to the credentials URI
                URL url = new URL(containerCredentialsUri);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // Add the authorization token if it's present
                if (containerToken != null && !containerToken.isEmpty()) {
                    connection.setRequestProperty("Authorization", containerToken);
                }

                // Read the response
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    throw new RuntimeException("Failed to retrieve credentials: HTTP " + responseCode);
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                }

                // Parse the JSON response
                String json = response.toString();

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

    private static String readUtf8(String file) {
        try {
            byte[] bytes = Files.readAllBytes(FileSystems.getDefault().getPath(file));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read string contents from file " + file);
        }
    }
}
