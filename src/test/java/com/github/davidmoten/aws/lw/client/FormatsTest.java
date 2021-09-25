package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;

import org.junit.Test;

import com.github.davidmoten.junit.Asserts;

public class FormatsTest {
    
    @Test
    public void isUtilityClass() {
        Asserts.assertIsUtilityClass(Formats.class);
    }
    
    @Test
    public void testFullDate() {
        String s = "Wed, 25 Aug 2021 21:55:47 GMT";
        TemporalAccessor t = Formats.FULL_DATE.parse(s);
        assertEquals(1629928547000L, Instant.from(t).toEpochMilli());
    }

}
