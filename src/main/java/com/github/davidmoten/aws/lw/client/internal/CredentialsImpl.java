package com.github.davidmoten.aws.lw.client.internal;

import com.github.davidmoten.aws.lw.client.Credentials;
import com.github.davidmoten.aws.lw.client.internal.util.Preconditions;
import java.time.Instant;
import java.util.Optional;

public final class CredentialsImpl implements Credentials {

    private final String accessKey;
    private final String secretKey;
    private final Optional<String> sessionToken;
    private final Optional<Instant> expiration;

    public CredentialsImpl(String accessKey, String secretKey, Optional<String> sessionToken) {
        this(accessKey, secretKey, sessionToken, Optional.empty());
    }

    public CredentialsImpl(String accessKey, String secretKey, Optional<String> sessionToken,
            Optional<Instant> expiration) {
        Preconditions.checkNotNull(accessKey);
        Preconditions.checkNotNull(secretKey);
        Preconditions.checkNotNull(sessionToken);
        Preconditions.checkNotNull(expiration);
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.sessionToken = sessionToken;
        this.expiration = expiration;
    }

    @Override
    public String accessKey() {
        return accessKey;
    }

    @Override
    public String secretKey() {
        return secretKey;
    }

    @Override
    public Optional<String> sessionToken() {
        return sessionToken;
    }

    @Override
    public Optional<Instant> expiration() {
        return expiration;
    }

}
