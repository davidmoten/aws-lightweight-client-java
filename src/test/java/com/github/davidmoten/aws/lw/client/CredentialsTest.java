package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;

import com.github.davidmoten.aws.lw.client.internal.Environment;
import com.github.davidmoten.aws.lw.client.internal.EnvironmentDefault;

public class CredentialsTest {

    @Test
    public void testFromEnvironment() {
        Environment instance = EnvironmentDefault.INSTANCE;
        EnvironmentDefault.INSTANCE = x -> "AWS_CONTAINER_CREDENTIALS_FULL_URI".equals(x) ? null : "thing";
        try {
            Credentials.fromEnvironment();
        } finally {
            EnvironmentDefault.INSTANCE = instance;
        }
    }

    @Test
    public void testFromSystemPropertiesWithV1Fallback() {
        System.setProperty("aws.accessKeyId", "ak-v1");
        System.setProperty("aws.secretKey", "sk-v1");
        System.setProperty("aws.sessionToken", "st-v1");
        try {
            Credentials c = Credentials.fromSystemProperties();
            assertEquals("ak-v1", c.accessKey());
            assertEquals("sk-v1", c.secretKey());
            assertTrue(c.sessionToken().isPresent());
            assertEquals("st-v1", c.sessionToken().get());
        } finally {
            System.clearProperty("aws.accessKeyId");
            System.clearProperty("aws.secretKey");
            System.clearProperty("aws.sessionToken");
        }
    }

    @Test
    public void testFromSystemPropertiesWithV2SecretKeyPreference() {
        System.setProperty("aws.accessKeyId", "ak-v2");
        System.setProperty("aws.secretAccessKey", "sk-v2");
        System.setProperty("aws.secretKey", "sk-v1-ignored");
        System.setProperty("aws.sessionToken", "st-v2");
        try {
            Credentials c = Credentials.fromSystemProperties();
            assertEquals("ak-v2", c.accessKey());
            assertEquals("sk-v2", c.secretKey());
            assertEquals("st-v2", c.sessionToken().get());
        } finally {
            System.clearProperty("aws.accessKeyId");
            System.clearProperty("aws.secretAccessKey");
            System.clearProperty("aws.secretKey");
            System.clearProperty("aws.sessionToken");
        }
    }

    @Test
    public void testExpirationDefaultReturnsEmptyForStaticCredentials() {
        Credentials c = Credentials.of("ak", "sk");
        assertFalse(c.expiration().isPresent());
    }

    @Test
    public void testExpirationDefaultReturnsEmptyWithSessionToken() {
        Credentials c = Credentials.of("ak", "sk", "st");
        assertFalse(c.expiration().isPresent());
    }
}
