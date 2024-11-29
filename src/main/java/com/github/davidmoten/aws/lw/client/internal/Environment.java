package com.github.davidmoten.aws.lw.client.internal;

import com.github.davidmoten.aws.lw.client.Credentials;

@FunctionalInterface
public interface Environment {

    String get(String name);

    default Credentials credentials() {
        return EnvironmentHelper.credentialsFromEnvironment(this, HttpClientDefault.INSTANCE);
    }

    static Environment instance() {
        return EnvironmentDefault.INSTANCE;
    }

}
