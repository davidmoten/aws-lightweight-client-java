package com.github.davidmoten.aws.lw.client.internal;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Test;

public class RetriesTest {

    @Test(expected = IllegalArgumentException.class)
    public void testBadJitter() {
        double jitter = -1;
        new Retries<Void>(100, 10, 2.0, jitter, 30000, x -> false, x -> false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadJitter2() {
        double jitter = 2;
        new Retries<Void>(100, 10, 2.0, jitter, 30000, x -> false, x -> false);
    }

    @Test(expected = OutOfMemoryError.class)
    public void testRethrowError() {
        Retries.rethrow(new OutOfMemoryError());
    }

    @Test(expected = NullPointerException.class)
    public void testRethrowRuntimeError() {
        Retries.rethrow(new NullPointerException());
    }

    @Test(expected = UncheckedIOException.class)
    public void testRethrowIOException() {
        Retries.rethrow(new IOException());
    }

    @Test(expected = RuntimeException.class)
    public void testRethrowException() {
        Retries.rethrow(new Exception());
    }

}
