package com.github.davidmoten.aws.lw.client.internal.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

public class UtilTest {

    @Test(expected = RuntimeException.class)
    public void testHash() {
        Util.hash("hi there".getBytes(StandardCharsets.UTF_8), "does not exist");
    }

    @Test(expected = RuntimeException.class)
    public void testToUrl() {
        Util.toUrl("bad");
    }

    @Test(expected = RuntimeException.class)
    public void testUrlEncode() {
        Util.urlEncode("abc://google.com", true, "does not exist");
    }

    @Test
    public void testCreateConnectionBad() throws IOException {
        Util.createHttpConnection(new URL("https://doesnotexist.never12345"), "GET",
                Collections.emptyMap(), 100, 100);
    }

    @Test
    public void testReadAndClose() {
        byte[] b = "hi there".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(b);
        assertArrayEquals(b, Util.readBytesAndClose(in));
    }

    @Test
    public void testReadAndCloseReadThrows() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream in = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("boo");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        try {
            Util.readBytesAndClose(in);
            Assert.fail();
        } catch (UncheckedIOException e) {
            // expected
            assertTrue(closed.get());
        }
    }

    @Test
    public void testReadAndCloseThrows() {
        InputStream in = new InputStream() {

            @Override
            public int read() throws IOException {
                return -1;
            }

            @Override
            public void close() throws IOException {
                throw new IOException("boo");
            }
        };
        try {
            Util.readBytesAndClose(in);
            Assert.fail();
        } catch (UncheckedIOException e) {
            // expected
        }
    }

}
