package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
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
    }

}
