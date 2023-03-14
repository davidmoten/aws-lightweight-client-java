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

public final class Retries<T> {

    private static final Set<Integer> transientStatusCodes = new HashSet<>( //
            Arrays.asList(400, 408, 500, 502, 503, 509));
    private static final Set<Integer> throttlingStatusCodes = new HashSet<>( //
            Arrays.asList(400, 403, 429, 502, 503, 509));

    public long initialIntervalMs = 100;
    public int maxAttempts = 10;
    public double backoffFactor = 2.0;
    public long maxIntervalMs = 30000;
    public Predicate<T> valueShouldRetry;
    public Predicate<Throwable> throwableShouldRetry;

    public static Retries<ResponseInputStream> requestRetries() {
        return new Retries<>( //
                500, //
                10, //
                2.0, //
                30000, //
                ris -> transientStatusCodes.contains(ris.statusCode())
                        || throttlingStatusCodes.contains(ris.statusCode()), //
                t -> t instanceof IOException || t instanceof UncheckedIOException);
    }

    public Retries(long initialIntervalMs, int maxAttempts, double backoffFactor, long maxIntervalMs,
            Predicate<T> valueShouldRetry, Predicate<Throwable> throwableShouldRetry) {
        this.initialIntervalMs = initialIntervalMs;
        this.maxAttempts = maxAttempts;
        this.backoffFactor = backoffFactor;
        this.maxIntervalMs = maxIntervalMs;
        this.valueShouldRetry = valueShouldRetry;
        this.throwableShouldRetry = throwableShouldRetry;
    }

    public Retries<T> copy() {
        return new Retries<>(initialIntervalMs, maxAttempts, backoffFactor, maxIntervalMs, valueShouldRetry,
                throwableShouldRetry);
    }

    public T call(Callable<T> callable) {
        long intervalMs = initialIntervalMs;
        int attempt = 0;
        while (true) {
            T value;
            try {
                attempt++;
                value = callable.call();
                if (!valueShouldRetry.test(value)) {
                    return value;
                }
                if (maxAttempts > 0 && attempt >= maxAttempts) {
                    return value;
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

    private static void rethrow(Throwable t) throws Error {
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
