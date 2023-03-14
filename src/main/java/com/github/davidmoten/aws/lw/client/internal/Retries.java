package com.github.davidmoten.aws.lw.client.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import com.github.davidmoten.aws.lw.client.MaxAttemptsExceededException;
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
    public Predicate<ResponseInputStream> statusCodeShouldRetry;
    public Predicate<Throwable> throwableShouldRetry;

    public Retries() {
        this( //
                500, //
                10, //
                2.0, //
                30000, //
                ris -> transientStatusCodes.contains(ris.statusCode())
                        || throttlingStatusCodes.contains(ris.statusCode()), //
                t -> t instanceof IOException || t instanceof UncheckedIOException);
    }

    public Retries(long initialIntervalMs, int maxAttempts, double backoffFactor, long maxIntervalMs,
            Predicate<ResponseInputStream> statusCodeShouldRetry, Predicate<Throwable> throwableShouldRetry) {
        this.initialIntervalMs = initialIntervalMs;
        this.maxAttempts = maxAttempts;
        this.backoffFactor = backoffFactor;
        this.maxIntervalMs = maxIntervalMs;
        this.statusCodeShouldRetry = statusCodeShouldRetry;
        this.throwableShouldRetry = throwableShouldRetry;
    }

    public Retries copy() {
        return new Retries(initialIntervalMs, maxAttempts, backoffFactor, maxIntervalMs, statusCodeShouldRetry,
                throwableShouldRetry);
    }

    public ResponseInputStream call(Callable<ResponseInputStream> callable) {
        long intervalMs = initialIntervalMs;
        int attempt = 0;
        while (true) {
            ResponseInputStream ris;
            try {
                attempt++;
                ris = callable.call();
                if (!statusCodeShouldRetry.test(ris)) {
                    return ris;
                }
                if (maxAttempts > 0 && attempt >= maxAttempts) {
                    return ris;
                }
            } catch (Throwable t) {
                if (!throwableShouldRetry.test(t)) {
                    rethrow(t);
                }
                if (maxAttempts > 0 && attempt >= maxAttempts) {
                    throw new MaxAttemptsExceededException("exceeded max attempts " + maxAttempts, t);
                }
            } finally {
                intervalMs = Math.min(maxIntervalMs, Math.round(backoffFactor * intervalMs));
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void rethrow(Throwable t) throws Error {
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
