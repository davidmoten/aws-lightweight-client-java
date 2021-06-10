package com.github.davidmoten.aws.lw.client.internal;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class EnvironmentDefaultTest {
    
    @Test
    public void testGet() {
        String key = System.getenv().keySet().stream().findFirst().get();
        assertNotNull(EnvironmentDefault.INSTANCE.get(key));
    }

}
