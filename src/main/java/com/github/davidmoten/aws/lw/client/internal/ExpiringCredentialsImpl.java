package com.github.davidmoten.aws.lw.client.internal;

import com.github.davidmoten.aws.lw.client.Credentials;
import com.github.davidmoten.aws.lw.client.internal.util.Preconditions;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

final class ExpiringCredentialsImpl implements Credentials {

    private static final long REFRESH_BEFORE_MS = 300_000; // 5 minutes

    private volatile Credentials current;
    private final Supplier<Credentials> refresher;
    private final Clock clock;

    private final Object lock = new Object();

    ExpiringCredentialsImpl(Credentials initial, Supplier<Credentials> refresher, Clock clock) {
        Preconditions.checkNotNull(initial);
        Preconditions.checkNotNull(refresher);
        Preconditions.checkNotNull(clock);
        this.current = initial;
        this.refresher = refresher;
        this.clock = clock;
    }

    @Override
    public String accessKey() {
        refreshIfNeeded();
        return current.accessKey();
    }

    @Override
    public String secretKey() {
        refreshIfNeeded();
        return current.secretKey();
    }

    @Override
    public Optional<String> sessionToken() {
        refreshIfNeeded();
        return current.sessionToken();
    }

    @Override
    public Optional<Instant> expiration() {
        refreshIfNeeded();
        return current.expiration();
    }

    private void refreshIfNeeded() {
        if (current.expiration().isPresent()) {
            Instant expiry = current.expiration().get();
            if (Instant.ofEpochMilli(clock.time()).isAfter(expiry.minusMillis(REFRESH_BEFORE_MS))) {
                synchronized (lock) {
                    if (current.expiration().isPresent()) {
                        Instant currentExpiry = current.expiration().get();
                        if (Instant.ofEpochMilli(clock.time()).isAfter(currentExpiry.minusMillis(REFRESH_BEFORE_MS))) {
                            try {
                                current = refresher.get();
                            } catch (RuntimeException e) {
                                if (Instant.ofEpochMilli(clock.time()).isAfter(currentExpiry)) {
                                    throw e;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
