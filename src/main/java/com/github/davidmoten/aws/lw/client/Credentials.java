package com.github.davidmoten.aws.lw.client;

import java.util.Optional;

import com.github.davidmoten.aws.lw.client.internal.CredentialsImpl;
import com.github.davidmoten.aws.lw.client.internal.Environment;

public interface Credentials {
    
    String accessKey();

    String secretKey();

    Optional<String> sessionToken();

    static Credentials of(String accessKey, String secretKey) {
        return new CredentialsImpl(accessKey, secretKey, Optional.empty());
    }
    
    static Credentials of(String accessKey, String secretKey, String sessionToken) {
        return new CredentialsImpl(accessKey, secretKey, Optional.of(sessionToken));
    }

    static Credentials fromEnvironment() {
        return Environment.DEFAULT.credentials();
    }
    
    static Credentials fromSystemProperties() {
        return new CredentialsImpl(System.getProperty("aws.accessKeyId"),
                System.getProperty("aws.secretKey"),
                Optional.empty());
    }

}
