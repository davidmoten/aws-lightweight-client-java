package com.github.davidmoten.aws.lw.client;

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

}
