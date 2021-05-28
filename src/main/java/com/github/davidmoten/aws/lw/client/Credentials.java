package com.github.davidmoten.aws.lw.client;

import com.github.davidmoten.aws.lw.client.internal.CredentialsImpl;

public interface Credentials {

    String accessKey();

    String secretKey();

    static Credentials of(String accessKey, String secretKey) {
        return new CredentialsImpl(accessKey, secretKey);
    }

    static Credentials fromEnvironment() {
        return of(System.getenv("AWS_ACCESS_KEY_ID"), System.getenv("AWS_SECRET_ACCESS_KEY"));
    }

}
