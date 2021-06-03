package com.github.davidmoten.aws.lw.client;

import org.junit.Test;

public class CredentialsTest {
    
    @Test(expected=IllegalArgumentException.class)
    public void testFromEnvironment() {
        Credentials.fromEnvironment();
    }

}
