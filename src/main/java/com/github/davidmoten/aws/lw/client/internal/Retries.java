package com.github.davidmoten.aws.lw.client.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import com.github.davidmoten.aws.lw.client.MaxAttemptsExceededException;

public final class Retries<T> {

    private long initialIntervalMs;
    private int maxAttempts;
    private double backoffFactor;
    private long maxIntervalMs;
    private Predicate<? super T> valueShouldRetry;
    private Predicate<? super Throwable> throwableShouldRetry;

    public Retries(long initialIntervalMs, int maxAttempts, double backoffFactor, long maxIntervalMs,
            Predicate<? super T> valueShouldRetry, Predicate<? super Throwable> throwableShouldRetry) {
        this.initialIntervalMs = initialIntervalMs;
        this.maxAttempts = maxAttempts;
        this.backoffFactor = backoffFactor;
        this.maxIntervalMs = maxIntervalMs;
        this.valueShouldRetry = valueShouldRetry;
        this.throwableShouldRetry = throwableShouldRetry;
    }

    public static <T> Retries<T> create(Predicate<? super T> valueShouldRetry,
            Predicate<? super Throwable> throwableShouldRetry) {
        return new Retries<T>( //
                500, //
                10, //
                2.0, //
                30000, //
                valueShouldRetry, //
                throwableShouldRetry);
    }

    public T call(Callable<T> callable) {
        return call(callable, valueShouldRetry);
    }

    public <S> S call(Callable<S> callable, Predicate<? super S> valueShouldRetry) {
        long intervalMs = initialIntervalMs;
        int attempt = 0;
        while (true) {
            S value;
            try {
                attempt++;
                value = callable.call();
                if (!valueShouldRetry.test(value)) {
                    return value;
                }
                if (maxAttempts > 0 && attempt >= maxAttempts) {
                    // note that caller is not aware that maxAttempts were reached, the caller just
                    // receives the last error response
                    return value;
                }
            } catch (Throwable t) {
                if (!throwableShouldRetry.test(t)) {
                    rethrow(t);
                }
                if (maxAttempts > 0 && attempt >= maxAttempts) {
                    throw new MaxAttemptsExceededException("exceeded max attempts " + maxAttempts, t);
                }
            } 
            intervalMs = Math.round(backoffFactor * intervalMs);
            if (maxIntervalMs > 0) {
                intervalMs = Math.min(maxIntervalMs, intervalMs);
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public <S> Retries<S> withValueShouldRetry(Predicate<? super S> valueShouldRetry) {
        return create(valueShouldRetry, throwableShouldRetry);
    }

    public void setInitialIntervalMs(long initialIntervalMs) {
        this.initialIntervalMs = initialIntervalMs;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void setBackoffFactor(double backoffFactor) {
        this.backoffFactor = backoffFactor;
    }

    public void setMaxIntervalMs(long maxIntervalMs) {
        this.maxIntervalMs = maxIntervalMs;
    }

    public void setValueShouldRetry(Predicate<? super T> valueShouldRetry) {
        this.valueShouldRetry = valueShouldRetry;
    }

    public void setThrowableShouldRetry(Predicate<? super Throwable> throwableShouldRetry) {
        this.throwableShouldRetry = throwableShouldRetry;
    }

    public Retries<T> copy() {
        return new Retries<>(initialIntervalMs, maxAttempts, backoffFactor, maxIntervalMs, valueShouldRetry,
                throwableShouldRetry);
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
