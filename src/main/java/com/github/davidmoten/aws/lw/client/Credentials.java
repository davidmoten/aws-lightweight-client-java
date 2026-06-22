package com.github.davidmoten.aws.lw.client;

import java.time.Instant;
import java.util.Optional;

import com.github.davidmoten.aws.lw.client.internal.CredentialsImpl;
import com.github.davidmoten.aws.lw.client.internal.Environment;

public interface Credentials {

    String accessKey();

    String secretKey();

    Optional<String> sessionToken();

    default Optional<Instant> expiration() {
        return Optional.empty();
    }

    static Credentials of(String accessKey, String secretKey) {
        return new CredentialsImpl(accessKey, secretKey, Optional.empty());
    }

    static Credentials of(String accessKey, String secretKey, String sessionToken) {
        return new CredentialsImpl(accessKey, secretKey, Optional.of(sessionToken));
    }

    static Credentials fromEnvironment() {
        return Environment.instance().credentials();
    }

    static Credentials fromSystemProperties() {
        String sk = System.getProperty("aws.secretAccessKey");
        if (sk == null) {
            sk = System.getProperty("aws.secretKey");
        }
        return new CredentialsImpl(System.getProperty("aws.accessKeyId"), sk,
                Optional.ofNullable(System.getProperty("aws.sessionToken")));
    }

}
