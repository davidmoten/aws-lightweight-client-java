package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ResponseTest {

    @Test
    public void test() {
        byte[] content = "hi there".getBytes(StandardCharsets.UTF_8);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-amz-meta-color", Collections.singletonList("red"));
        headers.put("x-amz-meta-thing", Collections.singletonList("under"));
        headers.put("blah", Collections.singletonList("stuff"));
        Response r = new Response(headers, content, 200);
        assertEquals("hi there", r.contentUtf8());
        assertTrue(r.isOk());
        assertEquals("red", r.metadata("color").get());
        assertEquals("under", r.metadata("thing").get());
        assertEquals(2, r.metadata().entrySet().size());
        assertEquals(3, r.headers().size());
        assertEquals(200, r.statusCode());
    }

    @Test
    public void testFilterNullKeys() {
        byte[] content = "hi there".getBytes(StandardCharsets.UTF_8);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-amz-meta-color", Collections.singletonList("red"));
        headers.put((String) null, Collections.singletonList("thing"));
        Response r = new Response(headers, content, 200);
        assertEquals(1, r.metadata().entrySet().size());
        assertEquals(2, r.headers().size());
    }

    @Test
    public void testResponseCodeOk() {
        Response r = new Response(Collections.emptyMap(), new byte[0], 210);
        assertTrue(r.isOk());
    }

    @Test
    public void testResponseCode199() {
        Response r = new Response(Collections.emptyMap(), new byte[0], 199);
        assertTrue(!r.isOk());
    }

    @Test
    public void testResponseCode300() {
        Response r = new Response(Collections.emptyMap(), new byte[0], 300);
        assertTrue(!r.isOk());
    }

    @Test
    public void testExists200() {
        Response r = new Response(Collections.emptyMap(), new byte[0], 200);
        assertTrue(r.exists());
    }

    @Test
    public void testExists299() {
        Response r = new Response(Collections.emptyMap(), new byte[0], 299);
        assertTrue(r.exists());
    }

    @Test
    public void testExists404() {
        Response r = new Response(Collections.emptyMap(), new byte[0], 404);
        assertFalse(r.exists());
    }

    @Test(expected = ServiceException.class)
    public void testExists500() {
        Response r = new Response(Collections.emptyMap(), new byte[0], 500);
        r.exists();
    }

    @Test(expected = ServiceException.class)
    public void testExists100() {
        Response r = new Response(Collections.emptyMap(), new byte[0], 100);
        r.exists();
    }
    
    @Test
    public void testFirstHeader() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("thing", Arrays.asList("a", "b"));
        Response r = new Response(map, new byte[0], 100);
        assertEquals("a", r.firstHeader("thing").get());
    }

    @Test
    public void testFirstHeaderFullDateNoHeaders() {
        Map<String, List<String>> map = new HashMap<>();
        Response r = new Response(map, new byte[0], 100);
        assertFalse(r.firstHeaderFullDate("date").isPresent());
    }

    @Test
    public void testFirstHeaderFullDateBlank() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("date", Collections.emptyList());
        Response r = new Response(map, new byte[0], 100);
        assertFalse(r.firstHeaderFullDate("date").isPresent());
    }

    @Test
    public void testFirstHeaderFullDate() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("date",
                Arrays.asList("Wed, 25 Aug 2021 21:55:47 GMT", "Thu, 26 Aug 2021 21:55:47 GMT"));
        Response r = new Response(map, new byte[0], 100);
        assertEquals(1629928547000L, r.firstHeaderFullDate("date").get().toEpochMilli());
    }
}
