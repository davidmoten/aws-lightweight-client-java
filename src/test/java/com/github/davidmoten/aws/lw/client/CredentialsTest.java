package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

import com.github.davidmoten.aws.lw.client.internal.Environment;
import com.github.davidmoten.aws.lw.client.internal.EnvironmentDefault;
import com.github.davidmoten.aws.lw.client.internal.EnvironmentHelper;
import com.github.davidmoten.guavamini.Maps;
import com.github.davidmoten.http.test.server.Server;

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
