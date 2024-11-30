package com.github.davidmoten.aws.lw.client.internal;

import com.github.davidmoten.aws.lw.client.Credentials;
import com.github.davidmoten.aws.lw.client.HttpClient;

@FunctionalInterface
public interface Environment {

    String get(String name);

    default Credentials credentials() {
        return EnvironmentHelper.credentialsFromEnvironment(this, HttpClient.defaultClient());
    }

    static Environment instance() {
        return EnvironmentDefault.INSTANCE;
    }

}
