package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
    public void testHasBodyWhenContentLengthPresent() throws IOException {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-length", Collections.singletonList("3"));
        try (ResponseInputStream r = new ResponseInputStream(() ->{}, 200, headers,
                new ByteArrayInputStream(new byte[] { 1, 2, 3 }))) {
            assertTrue(Request.hasBody(r));
        }
    }

    @Test
    public void testHasBodyWhenChunked() throws IOException {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("transfer-encoding", Collections.singletonList("chunkeD"));
        try (ResponseInputStream r = new ResponseInputStream(() ->{}, 200, headers,
                new ByteArrayInputStream(new byte[] { 1, 2, 3 }))) {
            assertTrue(Request.hasBody(r));
        }
    }

    @Test
    public void testHasBodyButNoHeader() throws IOException {
        Map<String, List<String>> headers = new HashMap<>();
        try (ResponseInputStream r = new ResponseInputStream(() ->{}, 200, headers,
                new ByteArrayInputStream(new byte[] { 1, 2, 3 }))) {
            assertFalse(Request.hasBody(r));
        }
    }
    
    @Test
    public void testTrimAndEnsureHasTrailingSlash() {
        assertEquals("/",Request.trimAndEnsureHasTrailingSlash(""));
        assertEquals("/",Request.trimAndEnsureHasTrailingSlash("/"));
        assertEquals("abc/",Request.trimAndEnsureHasTrailingSlash("abc"));
        assertEquals("abc/",Request.trimAndEnsureHasTrailingSlash("abc/"));
    }

}
