package com.github.davidmoten.aws.lw.client.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.github.davidmoten.aws.lw.client.Credentials;
import com.github.davidmoten.aws.lw.client.HttpClient;
import com.github.davidmoten.aws.lw.client.HttpClientTesting;
import com.github.davidmoten.guavamini.Maps;
import com.github.davidmoten.http.test.server.Server;
import com.github.davidmoten.junit.Asserts;

public class CredentialsHelperTest {

    @Test
    public void testIsUtilityClass() {
        Asserts.assertIsUtilityClass(CredentialsHelper.class);
    }

    @Test
    public void testFromContainer() {
        try (Server server = Server.start()) {
            Map<String, String> map = Maps //
                    .put("AWS_CONTAINER_CREDENTIALS_FULL_URI", server.baseUrl()) //
                    .put("AWS_CONTAINER_AUTHORIZATION_TOKEN", "abcde") //
                    .buildImmutable();
            server.response().statusCode(200)
                    .body("{\"AccessKeyId\":\"123\", \"SecretAccessKey\":\"secret\", \"Token\": \"token\"}").add();
            Environment env = x -> map.get(x);
            Credentials c = CredentialsHelper.credentialsFromEnvironment(env, HttpClient.defaultClient());
            assertEquals("123", c.accessKey());
            assertEquals("secret", c.secretKey());
            assertEquals("token", c.sessionToken().get());
        }
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromContainerIOException() {
        try (Server server = Server.start()) {
            Map<String, String> map = Maps //
                    .put("AWS_CONTAINER_CREDENTIALS_FULL_URI", server.baseUrl()) //
                    .put("AWS_CONTAINER_AUTHORIZATION_TOKEN", "abcde") //
                    .buildImmutable();
            server.response().statusCode(200)
                    .body("{\"AccessKeyId\":\"123\", \"SecretAccessKey\":\"secret\", \"Token\": \"token\"}").add();
            Environment env = x -> map.get(x);
            CredentialsHelper.credentialsFromEnvironment(env, HttpClientTesting.THROWING);
        }
    }

    @Test(expected = RuntimeException.class)
    public void testFromContainerHttpError() {
        try (Server server = Server.start()) {
            Map<String, String> map = Maps //
                    .put("AWS_CONTAINER_CREDENTIALS_FULL_URI", server.baseUrl()) //
                    .put("AWS_CONTAINER_AUTHORIZATION_TOKEN", "abcde") //
                    .buildImmutable();
            server.response().statusCode(500).add();
            Environment env = x -> map.get(x);
            CredentialsHelper.credentialsFromEnvironment(env, HttpClient.defaultClient());
        }
    }

    @Test
    public void testTokenFromFile() {
        assertEquals("something", CredentialsHelper.readUtf8("src/test/resources/test.txt"));
    }

    @Test(expected = IllegalStateException.class)
    public void testTokenFromFileDoesNotExist() {
        CredentialsHelper.readUtf8("doesNotExist");
    }

    @Test
    public void testResolveContainerTokenFromFile() {
        assertEquals("something", CredentialsHelper.resolveContainerToken(null, "src/test/resources/test.txt"));
    }

    @Test
    public void testResolveContainerTokenIfAlreadyPresent() {
        assertEquals("something", CredentialsHelper.resolveContainerToken("something", null));
    }

    @Test(expected = IllegalStateException.class)
    public void testResolveContainerTokenNeitherPresent() {
        CredentialsHelper.resolveContainerToken(null, null);
    }

    @Test
    public void testEnvironmentVariablesV1Fallback() {
        Map<String, String> map = Maps //
                .put("AWS_ACCESS_KEY", "ak-v1") //
                .put("AWS_SECRET_KEY", "sk-v1") //
                .put("AWS_SESSION_TOKEN", "st-v1") //
                .buildImmutable();
        Environment env = map::get;
        Credentials c = CredentialsHelper.credentialsFromEnvironment(env, HttpClient.defaultClient());
        assertEquals("ak-v1", c.accessKey());
        assertEquals("sk-v1", c.secretKey());
        assertEquals("st-v1", c.sessionToken().get());
    }

    @Test
    public void testEnvironmentVariablesV2PreferredOverV1() {
        Map<String, String> map = Maps //
                .put("AWS_ACCESS_KEY_ID", "ak-v2") //
                .put("AWS_ACCESS_KEY", "ak-v1-ignored") //
                .put("AWS_SECRET_ACCESS_KEY", "sk-v2") //
                .put("AWS_SECRET_KEY", "sk-v1-ignored") //
                .buildImmutable();
        Environment env = map::get;
        Credentials c = CredentialsHelper.credentialsFromEnvironment(env, HttpClient.defaultClient());
        assertEquals("ak-v2", c.accessKey());
        assertEquals("sk-v2", c.secretKey());
        assertFalse(c.sessionToken().isPresent());
    }

    @Test
    public void testContainerCredentialsRelativeUri() {
        try (Server server = Server.start()) {
            Map<String, String> map = Maps //
                    .put("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", server.baseUrl()) //
                    .put("AWS_CONTAINER_AUTHORIZATION_TOKEN", "abcde") //
                    .buildImmutable();
            server.response().statusCode(200)
                    .body("{\"AccessKeyId\":\"rel\", \"SecretAccessKey\":\"rel-secret\", \"Token\": \"rel-token\"}")
                    .add();
            Environment env = x -> map.get(x);
            Credentials c = CredentialsHelper.credentialsFromEnvironment(env, HttpClient.defaultClient());
            assertEquals("rel", c.accessKey());
            assertEquals("rel-secret", c.secretKey());
            assertEquals("rel-token", c.sessionToken().get());
        }
    }

    @Test(expected = RuntimeException.class)
    public void testAllProvidersExhaustedThrows() {
        // Disable IMDS so we don't try a real network call to 169.254.169.254
        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_DISABLED", "true") //
                .buildImmutable();
        Environment env = map::get;
        CredentialsHelper.credentialsFromEnvironment(env, HttpClient.defaultClient());
    }

    @Test
    public void testEnvironmentVariablesOnlyAccessKeySkipsToNextProvider() {
        try (Server server = Server.start()) {
            Map<String, String> map = Maps //
                    .put("AWS_ACCESS_KEY_ID", "ak-only") //
                    .put("AWS_CONTAINER_CREDENTIALS_FULL_URI", server.baseUrl()) //
                    .put("AWS_CONTAINER_AUTHORIZATION_TOKEN", "abcde") //
                    .buildImmutable();
            server.response().statusCode(200)
                    .body("{\"AccessKeyId\":\"container-ak\", \"SecretAccessKey\":\"container-sk\", \"Token\": \"t\"}")
                    .add();
            Environment env = map::get;
            Credentials c = CredentialsHelper.credentialsFromEnvironment(env, HttpClient.defaultClient());
            assertEquals("container-ak", c.accessKey());
            assertEquals("container-sk", c.secretKey());
        }
    }

    @Test
    public void testEnvironmentVariablesOnlySecretKeySkipsToNextProvider() {
        try (Server server = Server.start()) {
            Map<String, String> map = Maps //
                    .put("AWS_SECRET_ACCESS_KEY", "sk-only") //
                    .put("AWS_CONTAINER_CREDENTIALS_FULL_URI", server.baseUrl()) //
                    .put("AWS_CONTAINER_AUTHORIZATION_TOKEN", "abcde") //
                    .buildImmutable();
            server.response().statusCode(200)
                    .body("{\"AccessKeyId\":\"container-ak\", \"SecretAccessKey\":\"container-sk\", \"Token\": \"t\"}")
                    .add();
            Environment env = map::get;
            Credentials c = CredentialsHelper.credentialsFromEnvironment(env, HttpClient.defaultClient());
            assertEquals("container-ak", c.accessKey());
            assertEquals("container-sk", c.secretKey());
        }
    }

    @Test
    public void testWebIdentityCredentialsResolvedInChain() {
        try (Server server = Server.start()) {
            String xml = "<AssumeRoleWithWebIdentityResponse xmlns=\"https://sts.amazonaws.com/doc/2011-06-15/\">\n"
                    + "  <AssumeRoleWithWebIdentityResult>\n"
                    + "    <Credentials>\n"
                    + "      <AccessKeyId>STS-AK</AccessKeyId>\n"
                    + "      <SecretAccessKey>STS-SK</SecretAccessKey>\n"
                    + "      <SessionToken>STS-TOKEN</SessionToken>\n"
                    + "      <Expiration>2099-01-01T00:00:00Z</Expiration>\n"
                    + "    </Credentials>\n"
                    + "  </AssumeRoleWithWebIdentityResult>\n"
                    + "</AssumeRoleWithWebIdentityResponse>";
            server.response().statusCode(200).body(xml).add();
            Map<String, String> map = Maps //
                    .put("AWS_STS_ENDPOINT_URL", server.baseUrl()) //
                    .put("AWS_WEB_IDENTITY_TOKEN_FILE", "src/test/resources/test.txt") //
                    .put("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/test-role") //
                    .buildImmutable();
            Environment env = map::get;
            Credentials c = CredentialsHelper.credentialsFromEnvironment(env, HttpClient.defaultClient());
            assertEquals("STS-AK", c.accessKey());
            assertEquals("STS-SK", c.secretKey());
            assertEquals("STS-TOKEN", c.sessionToken().get());
        }
    }
}
