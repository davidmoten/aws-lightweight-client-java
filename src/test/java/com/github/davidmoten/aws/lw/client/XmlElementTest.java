package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.NoSuchElementException;

import org.junit.Test;

import com.github.davidmoten.xml.XmlElement;
import com.github.davidmoten.xml.XmlParseException;

public class XmlElementTest {

    @Test(expected = XmlParseException.class)
    public void testBlank() {
        XmlElement.parse("");
    }

    @Test(expected = XmlParseException.class)
    public void testNotClosed() {
        XmlElement.parse("<a>hello");
    }

    @Test(expected = XmlParseException.class)
    public void testNoStartTag() {
        XmlElement.parse("a");
    }

    @Test
    public void testNoContent() {
        XmlElement x = XmlElement.parse("<a/>");
        assertEquals("a", x.name());
        assertFalse(x.hasChildren());
        assertTrue(x.attributeNames().isEmpty());
        assertEquals("", x.content());
    }

    @Test
    public void testEmptyContent() {
        XmlElement x = XmlElement.parse("<a></a>");
        assertEquals("a", x.name());
        assertFalse(x.hasChildren());
        assertTrue(x.attributeNames().isEmpty());
        assertEquals("", x.content());
    }

    @Test
    public void testHasContentAndWhiteSpaceTrimmed() {
        XmlElement x = XmlElement.parse("<a>\t\n hi there -&gt; 1 \n\t</a>");
        assertEquals("a", x.name());
        assertFalse(x.hasChildren());
        assertTrue(x.attributeNames().isEmpty());
        assertEquals("hi there -> 1", x.content());
    }

    @Test
    public void testHasChild() {
        XmlElement x = XmlElement.parse("<a><b/></a>");
        assertEquals("a", x.name());
        assertTrue(x.hasChildren());
        assertTrue(x.attributeNames().isEmpty());
        assertEquals("b", x.firstChild().name());
        assertEquals("", x.firstChild().content());
    }

    @Test
    public void testHasTwoChildren() {
        String xml = "<a><b>boo</b><c step=\"large\">bingo</c></a>";
        XmlElement x = XmlElement.parse(xml);
        assertEquals("a", x.name());
        assertTrue(x.hasChildren());
        assertEquals(2, x.countChildren());
        assertEquals(2, x.children().size());
        assertEquals("bingo", x.content("c"));
        assertTrue(x.attributeNames().isEmpty());
        assertEquals("b", x.firstChild().name());
        assertEquals("boo", x.firstChild().content());
        assertEquals("c", x.child("c").name());
        assertEquals("bingo", x.child(1).content());
        assertEquals("large", x.child("c").attribute("step"));
        assertEquals("<a><b>boo</b><c step=\"large\">bingo</c></a>", x.toString());
        assertEquals(0, x.lineNumber());
    }

    @Test(expected = NoSuchElementException.class)
    public void testNoChild() {
        XmlElement x = XmlElement.parse("<a/>");
        x.child("b");
    }
}
