package com.github.davidmoten.aws.lw.client.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import com.github.davidmoten.aws.lw.client.ResponseInputStream;

public final class Retries {

    private static final Set<Integer> transientStatusCodes = new HashSet<>( //
            Arrays.asList(400, 408, 500, 502, 503, 509));
    private static final Set<Integer> throttlingStatusCodes = new HashSet<>( //
            Arrays.asList(400, 403, 429, 502, 503, 509));

    public long initialIntervalMs = 100;
    public int maxAttempts = 10;
    public double backoffFactor = 2.0;
    public long maxIntervalMs = 30000;
    public Predicate<ResponseInputStream> statusCodeShouldRetry = ris -> transientStatusCodes.contains(ris.statusCode())
            || throttlingStatusCodes.contains(ris.statusCode());
    public Predicate<Throwable> throwableShouldRetry = t -> false;

    public ResponseInputStream call(Callable<ResponseInputStream> callable) {
        long intervalMs = initialIntervalMs;
        int attempt = 0;
        while (true) {
            try {
                ResponseInputStream ris = callable.call();
                if (!statusCodeShouldRetry.test(ris)) {
                    return ris;
                }
                attempt++;
                if (maxAttempts > 0 && attempt >= maxAttempts) {
                    return ris;
                }
                intervalMs = Math.min(maxIntervalMs, Math.round(backoffFactor * intervalMs));
                Thread.sleep(intervalMs);
            } catch (Throwable t) {
                if (!throwableShouldRetry.test(t)) {
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    } else if (t instanceof Error) {
                        throw (Error) t;
                    } else if (t instanceof IOException) {
                        throw new UncheckedIOException((IOException) t);
                    } else {
                        throw new RuntimeException(t);
                    }
                }
            }
        }
    }

}
