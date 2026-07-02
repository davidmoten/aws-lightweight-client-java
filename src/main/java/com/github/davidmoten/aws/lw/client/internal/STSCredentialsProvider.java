package com.github.davidmoten.aws.lw.client.internal;

import com.github.davidmoten.aws.lw.client.BaseUrlFactory;
import com.github.davidmoten.aws.lw.client.Credentials;
import com.github.davidmoten.aws.lw.client.HttpClient;
import com.github.davidmoten.aws.lw.client.ResponseInputStream;
import com.github.davidmoten.aws.lw.client.internal.util.Util;
import com.github.davidmoten.aws.lw.client.xml.XmlElement;
import com.github.davidmoten.aws.lw.client.xml.XmlParseException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

final class STSCredentialsProvider {

    private static final int STS_CONNECT_TIMEOUT_MS = 10000;
    private static final int STS_READ_TIMEOUT_MS = 10000;

    private STSCredentialsProvider() {
        // prevent instantiation
    }

    static Optional<Credentials> credentials(Environment env, HttpClient httpClient, Clock clock) {
        String tokenFile = tokenFile(env);
        String roleArn = roleArn(env);
        if (tokenFile == null || roleArn == null) {
            return Optional.empty();
        }
        String sessionName = sessionName(env);
        Supplier<Credentials> refresher = () -> {
            String token = CredentialsHelper.readUtf8(tokenFile);
            return assumeRoleWithWebIdentity(httpClient, env, token, roleArn, sessionName);
        };
        Credentials initial = refresher.get();
        return Optional.of(new ExpiringCredentialsImpl(initial, refresher, clock));
    }

    private static String tokenFile(Environment env) {
        String s = System.getProperty("aws.webIdentityTokenFile");
        if (s != null) {
            return s;
        }
        return env.get("AWS_WEB_IDENTITY_TOKEN_FILE");
    }

    private static String roleArn(Environment env) {
        String s = System.getProperty("aws.roleArn");
        if (s != null) {
            return s;
        }
        return env.get("AWS_ROLE_ARN");
    }

    private static String sessionName(Environment env) {
        String s = System.getProperty("aws.roleSessionName");
        if (s != null) {
            return s;
        }
        String v = env.get("AWS_ROLE_SESSION_NAME");
        if (v != null) {
            return v;
        }
        return "aws-lw-client-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // VisibleForTesting
    static String stsEndpoint(Environment env) {
        String override = env.get("AWS_STS_ENDPOINT_URL");
        if (override != null) {
            return override;
        }
        String regional = System.getProperty("aws.stsRegionalEndpoints");
        if (regional == null) {
            regional = env.get("AWS_STS_REGIONAL_ENDPOINTS");
        }
        Optional<String> region = Optional.empty();
        if ("regional".equalsIgnoreCase(regional)) {
            String r = env.get("AWS_REGION");
            if (r == null) {
                r = env.get("AWS_DEFAULT_REGION");
            }
            region = Optional.ofNullable(r);
        }
        return BaseUrlFactory.DEFAULT.create("sts", region);
    }

    // VisibleForTesting
    static Credentials assumeRoleWithWebIdentity(HttpClient httpClient, Environment env,
            String token, String roleArn, String sessionName) {
        String url = stsEndpoint(env);
        byte[] body = buildRequestBody(roleArn, sessionName, token);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        try {
            URL endpointUrl = Util.toUrl(url);
            ResponseInputStream response = httpClient.request(endpointUrl, "POST", headers, body,
                    STS_CONNECT_TIMEOUT_MS, STS_READ_TIMEOUT_MS);
            String xml = new String(Util.readBytesAndClose(response), StandardCharsets.UTF_8);
            if (response.statusCode() != 200) {
                throw new RuntimeException("STS AssumeRoleWithWebIdentity failed: HTTP " + response.statusCode()
                        + " - " + xml);
            }
            return parseStsResponse(xml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // VisibleForTesting
    static byte[] buildRequestBody(String roleArn, String sessionName, String token) {
        String body = "Action=AssumeRoleWithWebIdentity" //
                + "&Version=2011-06-15" //
                + "&RoleArn=" + urlEncode(roleArn) //
                + "&RoleSessionName=" + urlEncode(sessionName) //
                + "&WebIdentityToken=" + urlEncode(token) //
                + "&DurationSeconds=3600";
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // VisibleForTesting
    static Credentials parseStsResponse(String xml) {
        try {
            XmlElement root = XmlElement.parse(xml);
            XmlElement creds;
            try {
                creds = root.child("AssumeRoleWithWebIdentityResult", "Credentials");
            } catch (RuntimeException e) {
                throw new RuntimeException(
                        "Failed to parse STS AssumeRoleWithWebIdentity response: " + xml, e);
            }
            String accessKeyId = creds.content("AccessKeyId");
            String secretAccessKey = creds.content("SecretAccessKey");
            String sessionToken = creds.content("SessionToken");
            String expirationStr = creds.content("Expiration");
            Instant expiration = Instant.parse(expirationStr);
            return new CredentialsImpl(accessKeyId, secretAccessKey, Optional.of(sessionToken),
                    Optional.of(expiration));
        } catch (XmlParseException e) {
            throw new RuntimeException("Failed to parse STS AssumeRoleWithWebIdentity response: " + xml, e);
        }
    }
}
