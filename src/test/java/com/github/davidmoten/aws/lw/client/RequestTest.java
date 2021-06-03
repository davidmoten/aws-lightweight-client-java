package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;

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

}
