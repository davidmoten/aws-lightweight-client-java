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
        assertEquals("a", x.name());
        assertFalse(x.hasChildren());
        assertFalse(x.enumerateAttributeNames().hasMoreElements());
        assertEquals("", x.content());
    }

    @Test
    public void testEmptyContent() {
        XmlElement x = XmlElement.parse("<a></a>");
        assertEquals("a", x.name());
        assertFalse(x.hasChildren());
        assertFalse(x.enumerateAttributeNames().hasMoreElements());
        assertEquals("", x.content());
    }

    @Test
    public void testHasContentAndWhiteSpaceTrimmed() {
        XmlElement x = XmlElement.parse("<a>\t\n hi there -&gt; 1 \n\t</a>");
        assertEquals("a", x.name());
        assertFalse(x.hasChildren());
        assertFalse(x.enumerateAttributeNames().hasMoreElements());
        assertEquals("hi there -> 1", x.content());
    }

    @Test
    public void testHasChild() {
        XmlElement x = XmlElement.parse("<a><b/></a>");
        assertEquals("a", x.name());
        assertTrue(x.hasChildren());
        assertFalse(x.enumerateAttributeNames().hasMoreElements());
        assertEquals("b", x.firstChild().name());
        assertEquals("", x.firstChild().content());
    }
    
    @Test
    public void testHasTwoChildren() {
        String xml = "<a><b>boo</b><c step=\"large\">bingo</c></a>";
        XmlElement x = XmlElement.parse(xml);
        assertEquals("a", x.name());
        assertTrue(x.hasChildren());
        assertFalse(x.enumerateAttributeNames().hasMoreElements());
        assertEquals("b", x.firstChild().name());
        assertEquals("boo", x.firstChild().content());
        assertEquals("c", x.child(1).name());
        assertEquals("bingo", x.child(1).content());
        assertEquals("large", x.child(1).attribute("step"));
    }
}
