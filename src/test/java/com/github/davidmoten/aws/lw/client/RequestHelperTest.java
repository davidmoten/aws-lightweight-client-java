package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.github.davidmoten.aws.lw.client.RequestHelper.Parameter;
import com.github.davidmoten.junit.Asserts;

public class RequestHelperTest {

    @Test
    public void isUtilityClass() {
        Asserts.assertIsUtilityClass(RequestHelper.class);
    }

    @Test
    public void testIsEmpty() {
        assertTrue(RequestHelper.isEmpty(null));
        assertTrue(RequestHelper.isEmpty(new byte[0]));
        assertFalse(RequestHelper.isEmpty(new byte[2]));
    }

    @Test
    public void testExtractQueryParameters() {
        List<Parameter> list = RequestHelper.extractQueryParameters("a=1&b=2");
        assertEquals(2, list.size());
        assertEquals("a", list.get(0).name);
        assertEquals("1", list.get(0).value);
        assertEquals("b", list.get(1).name);
        assertEquals("2", list.get(1).value);
    }

    @Test
    public void testExtractQueryParametersDoesNotHaveToHaveValue() {
        List<Parameter> list = RequestHelper.extractQueryParameters("hello");
        assertEquals("hello", list.get(0).name);
        assertNull(list.get(0).value);
    }

    @Test(expected = RuntimeException.class)
    public void testEncoding() {
        RequestHelper.parameter("name", "fred", "");
    }

}
