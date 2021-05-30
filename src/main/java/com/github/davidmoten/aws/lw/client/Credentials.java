package com.github.davidmoten.aws.lw.client;

import java.util.Optional;

import com.github.davidmoten.aws.lw.client.internal.CredentialsImpl;

public interface Credentials {

    String accessKey();

    String secretKey();

    Optional<String> sessionToken();

    static Credentials of(String accessKey, String secretKey) {
        return new CredentialsImpl(accessKey, secretKey, Optional.empty());
    }

    static Credentials fromEnvironment() {
        return new CredentialsImpl(System.getenv("AWS_ACCESS_KEY_ID"),
                System.getenv("AWS_SECRET_ACCESS_KEY"),
                Optional.ofNullable(System.getenv("AWS_SESSION_TOKEN")));
    }

}
