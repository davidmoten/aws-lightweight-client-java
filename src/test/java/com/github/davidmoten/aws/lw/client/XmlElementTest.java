package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.github.davidmoten.xml.XmlElement;

public class XmlElementTest {

    @Test
    public void testNoContent() {
        XmlElement x = XmlElement.parse("<a/>");
        assertEquals("a", x.getName());
        assertFalse(x.hasChildren());
        assertFalse(x.enumerateAttributeNames().hasMoreElements());
        assertEquals("", x.content());
    }

    @Test
    public void testEmptyContent() {
        XmlElement x = XmlElement.parse("<a></a>");
        assertEquals("a", x.getName());
        assertFalse(x.hasChildren());
        assertFalse(x.enumerateAttributeNames().hasMoreElements());
        assertEquals("", x.content());
    }

    @Test
    public void testHasContentAndWhiteSpaceTrimmed() {
        XmlElement x = XmlElement.parse("<a>\t\n hi there -&gt; 1 \n\t</a>");
        assertEquals("a", x.getName());
        assertFalse(x.hasChildren());
        assertFalse(x.enumerateAttributeNames().hasMoreElements());
        assertEquals("hi there -> 1", x.content());
    }

    @Test
    public void testHasChild() {
        XmlElement x = XmlElement.parse("<a><b/></a>");
        assertEquals("a", x.getName());
        assertTrue(x.hasChildren());
        assertFalse(x.enumerateAttributeNames().hasMoreElements());
        assertEquals("b", x.firstChild().getName());
        assertEquals("", x.firstChild().content());
    }
    
    @Test
    public void testHasTwoChildren() {
        XmlElement x = XmlElement.parse("<a><b>boo</b><c>bingo</c></a>");
        assertEquals("a", x.getName());
        assertTrue(x.hasChildren());
        assertFalse(x.enumerateAttributeNames().hasMoreElements());
        assertEquals("b", x.firstChild().getName());
        assertEquals("boo", x.firstChild().content());
        assertEquals("c", x.child(1).getName());
        assertEquals("bingo", x.child(1).content());
    }
}
