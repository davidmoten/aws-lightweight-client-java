package com.github.davidmoten.aws.lw.client.internal;

import java.util.Optional;

import com.github.davidmoten.aws.lw.client.Credentials;

@FunctionalInterface
public interface Environment {

    Environment DEFAULT = EnvironmentDefault.INSTANCE;

    String get(String name);

    default Credentials credentials() {
        return new CredentialsImpl(get("AWS_ACCESS_KEY_ID"), get("AWS_SECRET_ACCESS_KEY"),
                Optional.ofNullable(get("AWS_SESSION_TOKEN")));
    }
}
