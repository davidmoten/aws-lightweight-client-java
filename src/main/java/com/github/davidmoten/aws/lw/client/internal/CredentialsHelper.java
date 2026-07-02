package com.github.davidmoten.aws.lw.client.internal;

import com.github.davidmoten.aws.lw.client.Credentials;
import com.github.davidmoten.aws.lw.client.HttpClient;
import com.github.davidmoten.aws.lw.client.ResponseInputStream;
import com.github.davidmoten.aws.lw.client.internal.util.Util;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class CredentialsHelper {

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    private CredentialsHelper() {
        // prevent instantiation
    }

    static Credentials credentialsFromEnvironment(Environment env, HttpClient client) {
        // if using SnapStart we need to get the credentials from a local container
        // it is a precondition that SnapStart snapshot has happened before credentials get loaded
        // so we get a chance to refresh creds from the local container
		return credentialsFromEnvironment(env, client, Clock.DEFAULT);
	}

    static Credentials credentialsFromEnvironment(Environment env, HttpClient client, Clock clock) {
		// Attempting to load credentials in the order described in
		// https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/auth/credentials/DefaultCredentialsProvider.html
        // 1. Java System Properties
        String ak = System.getProperty("aws.accessKeyId");
        String sk = System.getProperty("aws.secretAccessKey", System.getProperty("aws.secretKey"));
        if (ak != null && sk != null) {
            return Credentials.fromSystemProperties();
        }

        // 2. Environment Variables
        ak = env.get("AWS_ACCESS_KEY_ID");
        if (ak == null) {
            ak = env.get("AWS_ACCESS_KEY");
        }
        sk = env.get("AWS_SECRET_ACCESS_KEY");
        if (sk == null) {
            sk = env.get("AWS_SECRET_KEY");
        }
        if (ak != null && sk != null) {
            return new CredentialsImpl(ak, sk, Optional.ofNullable(env.get("AWS_SESSION_TOKEN")));
        }

        // 3. Web Identity Token credentials via STS
        Optional<Credentials> webIdentity = STSCredentialsProvider.credentials(env, client, clock);
        if (webIdentity.isPresent()) {
            return webIdentity.get();
        }

        // 4. Container credentials
        String containerCredentialsUri = env.get("AWS_CONTAINER_CREDENTIALS_FULL_URI");
        if (containerCredentialsUri == null) {
            containerCredentialsUri = env.get("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI");
        }
        if (containerCredentialsUri != null) {
            String containerToken = env.get("AWS_CONTAINER_AUTHORIZATION_TOKEN");
            String containerTokenFile = env.get("AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE");
            containerToken = resolveContainerToken(containerToken, containerTokenFile);
            try {
                // Create a connection to the credentials URI
                URL url = URI.create(containerCredentialsUri).toURL();
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", containerToken);
                ResponseInputStream response = client.request(url, "GET", headers, null, CONNECT_TIMEOUT_MS,
                        READ_TIMEOUT_MS);

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to retrieve credentials: HTTP " + response.statusCode());
                }

                String json = new String(Util.readBytesAndClose(response), StandardCharsets.UTF_8);

                // Parse the JSON response
                String accessKeyId = Util.jsonFieldText(json, "AccessKeyId").get();
                String secretAccessKey = Util.jsonFieldText(json, "SecretAccessKey").get();
                String sessionToken = Util.jsonFieldText(json, "Token").get();
                return new CredentialsImpl(accessKeyId, secretAccessKey, Optional.of(sessionToken));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        // 5. Instance profile credentials via EC2 metadata service
        Optional<Credentials> instanceProfile = InstanceProfileCredentialsProvider.credentials(env, client, clock);
        if (instanceProfile.isPresent()) {
            return instanceProfile.get();
        }

        throw new RuntimeException(
                "Unable to load AWS credentials from environment: no system properties, "
                        + "environment variables, web identity token, container credentials, "
                        + "or instance profile credentials were found");
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
