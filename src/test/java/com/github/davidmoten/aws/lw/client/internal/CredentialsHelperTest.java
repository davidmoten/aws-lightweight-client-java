package com.github.davidmoten.aws.lw.client.internal;

import static org.junit.Assert.assertEquals;

import java.io.UncheckedIOException;
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

}
