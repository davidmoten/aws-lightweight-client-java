package com.github.davidmoten.aws.lw.client.internal;

import com.github.davidmoten.aws.lw.client.Credentials;
import com.github.davidmoten.aws.lw.client.HttpClient;
import com.github.davidmoten.aws.lw.client.ResponseInputStream;
import com.github.davidmoten.aws.lw.client.internal.util.Util;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

final class InstanceProfileCredentialsProvider {

    private static final String IMDS_DEFAULT_ENDPOINT = "http://169.254.169.254";
    private static final String TOKEN_PATH = "/latest/api/token";
    private static final String SECURITY_CREDENTIALS_PATH = "/latest/meta-data/iam/security-credentials/";
    private static final int TOKEN_TTL_SECONDS = 21600;
    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int READ_TIMEOUT_MS = 1000;

    private InstanceProfileCredentialsProvider() {
        // prevent instantiation
    }

    static Optional<Credentials> credentials(Environment env, HttpClient httpClient, Clock clock) {
        if (isDisabled(env)) {
            return Optional.empty();
        }
        String endpoint = endpoint(env);
        Supplier<Credentials> refresher = () -> fetchCredentials(endpoint, httpClient);
        try {
            Credentials initial = refresher.get();
            return Optional.of(new ExpiringCredentialsImpl(initial, refresher, clock));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean isDisabled(Environment env) {
        String v = System.getProperty("aws.disableEc2Metadata");
        if (v == null) {
            v = env.get("AWS_EC2_METADATA_DISABLED");
        }
        return "true".equalsIgnoreCase(v);
    }

    // VisibleForTesting
    static String endpoint(Environment env) {
        String v = System.getProperty("aws.ec2MetadataServiceEndpoint");
        if (v == null) {
            v = env.get("AWS_EC2_METADATA_SERVICE_ENDPOINT");
        }
        if (v != null) {
            return v;
        }
        return IMDS_DEFAULT_ENDPOINT;
    }

    private static Credentials fetchCredentials(String endpoint, HttpClient httpClient) {
        String token = fetchToken(endpoint, httpClient);
        String roleName = fetchRoleName(endpoint, httpClient, token);
        return fetchCredentialsForRole(endpoint, httpClient, token, roleName);
    }

    private static String fetchToken(String endpoint, HttpClient httpClient) {
        String tokenUrl = endpoint + TOKEN_PATH;
        Map<String, String> headers = new HashMap<>();
        headers.put("X-aws-ec2-metadata-token-ttl-seconds", String.valueOf(TOKEN_TTL_SECONDS));
        try {
            URL url = Util.toUrl(tokenUrl);
            ResponseInputStream response = httpClient.request(url, "PUT", headers, null,
                    CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            String token = new String(Util.readBytesAndClose(response), StandardCharsets.UTF_8).trim();
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to fetch IMDSv2 token: HTTP " + response.statusCode());
            }
            return token;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fetchRoleName(String endpoint, HttpClient httpClient, String token) {
        String url = endpoint + SECURITY_CREDENTIALS_PATH;
        Map<String, String> headers = new HashMap<>();
        headers.put("X-aws-ec2-metadata-token", token);
        try {
            URL endpointUrl = Util.toUrl(url);
            ResponseInputStream response = httpClient.request(endpointUrl, "GET", headers, null,
                    CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            String roleName = new String(Util.readBytesAndClose(response), StandardCharsets.UTF_8).trim();
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to fetch IMDS role name: HTTP " + response.statusCode());
            }
            if (roleName.isEmpty()) {
                throw new RuntimeException("No IAM role found in IMDS response");
            }
            return roleName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Credentials fetchCredentialsForRole(String endpoint, HttpClient httpClient,
            String token, String roleName) {
        String url = endpoint + SECURITY_CREDENTIALS_PATH + roleName;
        Map<String, String> headers = new HashMap<>();
        headers.put("X-aws-ec2-metadata-token", token);
        try {
            URL endpointUrl = Util.toUrl(url);
            ResponseInputStream response = httpClient.request(endpointUrl, "GET", headers, null,
                    CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            String json = new String(Util.readBytesAndClose(response), StandardCharsets.UTF_8);
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to fetch IMDS credentials: HTTP " + response.statusCode());
            }
            String accessKeyId = Util.jsonFieldText(json, "AccessKeyId")
                    .orElseThrow(() -> new RuntimeException(
                            "AccessKeyId not found in IMDS credentials response: " + json));
            String secretAccessKey = Util.jsonFieldText(json, "SecretAccessKey")
                    .orElseThrow(() -> new RuntimeException(
                            "SecretAccessKey not found in IMDS credentials response: " + json));
            String sessionToken = Util.jsonFieldText(json, "Token")
                    .orElseThrow(() -> new RuntimeException(
                            "Token not found in IMDS credentials response: " + json));
            String expirationStr = Util.jsonFieldText(json, "Expiration")
                    .orElseThrow(() -> new RuntimeException(
                            "Expiration not found in IMDS credentials response: " + json));
            Instant expiration = Instant.parse(expirationStr);
            return new CredentialsImpl(accessKeyId, secretAccessKey, Optional.of(sessionToken),
                    Optional.of(expiration));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
