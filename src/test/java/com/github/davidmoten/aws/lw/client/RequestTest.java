package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class RequestTest {

    @Test
    public void testTrim() {
        assertEquals("abc", Request.trimAndRemoveLeadingAndTrailingSlashes("abc"));
        assertEquals("abc", Request.trimAndRemoveLeadingAndTrailingSlashes("/abc"));
        assertEquals("abc", Request.trimAndRemoveLeadingAndTrailingSlashes(" /abc"));
        assertEquals("abc", Request.trimAndRemoveLeadingAndTrailingSlashes("abc/"));
        assertEquals("abc", Request.trimAndRemoveLeadingAndTrailingSlashes("abc/ "));
        assertEquals("abc", Request.trimAndRemoveLeadingAndTrailingSlashes("/abc/"));
    }

    @Test
    public void testHasBodyWhenContentLengthPresent() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-length", Collections.singletonList("3"));
        assertTrue(Request.hasBody(new ResponseInputStream(null, 200, headers,
                new ByteArrayInputStream(new byte[] {1, 2, 3}))));
    }

    @Test
    public void testHasBodyWhenChunked() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("transfer-encoding", Collections.singletonList("chunkeD"));
        assertTrue(Request.hasBody(new ResponseInputStream(null, 200, headers,
                new ByteArrayInputStream(new byte[] {1, 2, 3}))));
    }

    @Test
    public void testHasBodyButNoHeader() {
        Map<String, List<String>> headers = new HashMap<>();
        assertFalse(Request.hasBody(new ResponseInputStream(null, 200, headers,
                new ByteArrayInputStream(new byte[] {1, 2, 3}))));
    }

}
