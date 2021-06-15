package com.github.davidmoten.aws.lw.client.internal;

public final class EnvironmentDefault implements Environment {

    // mutable for testing
    public static Environment INSTANCE = new EnvironmentDefault();

    private EnvironmentDefault() {
        // prevent instantiation
    }

    @Override
    public String get(String name) {
        return System.getenv(name);
    }

}
