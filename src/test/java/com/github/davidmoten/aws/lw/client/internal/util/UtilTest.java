package com.github.davidmoten.aws.lw.client.internal.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

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
    public void testCreateConnectionBad() throws MalformedURLException {
        Util.createHttpConnection(new URL("https://doesnotexist.never12345"), "GET",
                Collections.emptyMap(), 100, 100);
    }

}
