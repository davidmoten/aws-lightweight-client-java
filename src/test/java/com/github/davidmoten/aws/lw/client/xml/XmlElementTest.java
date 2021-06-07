package com.github.davidmoten.aws.lw.client.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.NoSuchElementException;

import org.junit.Test;

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

    @Test(expected = UncheckedIOException.class)
    public void testReaderThrows() {
        XmlElement.parseUnchecked(new Reader() {

            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("boo");
            }

            @Override
            public void close() throws IOException {
                // do nothings
            }
        });
    }

    @Test
    public void testWithPreamble() {
        XmlElement x = XmlElement.parse("<?xml>\n<a/>");
        assertEquals("a", x.name());
    }

    @Test
    public void testWithPreamble2() {
        XmlElement x = XmlElement.parse("<?[xml]>\n<a/>");
        assertEquals("a", x.name());
    }

    @Test
    public void testWithPreamble3() {
        XmlElement x = XmlElement.parse("<?-xml>\n<a/>");
        assertEquals("a", x.name());
    }

    @Test
    public void testWithPreamble4() {
        XmlElement x = XmlElement.parse("<?--xml-->\n<a/>");
        assertEquals("a", x.name());
    }

    @Test
    public void testWithPreamble5() {
        XmlElement x = XmlElement.parse("<?-[xml*]>\n<a/>");
        assertEquals("a", x.name());
    }

    @Test
    public void testWithPreamble6() {
        XmlElement x = XmlElement.parse("<?-]xml>\n<a/>");
        assertEquals("a", x.name());
    }

    @Test
    public void testWithPreambleAndDocType() {
        XmlElement x = XmlElement.parse("<?XML version=\"1.0\"?>\n"
                + "<!DOCTYPE greeting SYSTEM \"hello.dtd\">\n" + "<a>Hello, world!</a>");
        assertEquals("a", x.name());
    }

    @Test
    public void testWithPreambleAndDocType2() {
        XmlElement x = XmlElement
                .parse("<?XML version=\"1.0\" encoding=\"UTF-8\" ?>\n" + "<!DOCTYPE greeting [\n"
                        + "  <!ELEMENT greeting (#PCDATA)>\n" + "]>\n" + "<a>Hello, world!</a>");
        assertEquals("a", x.name());
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxError1() {
        XmlElement.parse("<a x+\"123\"/>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxError2() {
        XmlElement.parse("<a/+>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxError3() {
        XmlElement.parse("<a><b>boo</b><-- hi there --></a>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxError4() {
        XmlElement.parse("<a><b>boo</b>+</a>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxError5() {
        XmlElement.parse("<a><b>boo<+b></a>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxError6() {
        XmlElement.parse("<a><b>boo</c></a>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxError7() {
        XmlElement.parse("<a><b>boo</b z></a>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxErrorBadComment() {
        XmlElement.parse("<- hi there -->");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxErrorBadComment2() {
        XmlElement.parse("<a><+ hi there --></a>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxErrorBadComment3() {
        XmlElement.parse("<a><b>boo</b><! hi there --></a>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxErrorBadComment4() {
        XmlElement.parse("<a><b>boo</b><!- hi there --></a>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxErrorBadComment5() {
        XmlElement.parse("<a><b>boo</b><!- hi there --> +</a>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxBadEntity() {
        XmlElement.parse("<a>&#x^;</a>");
    }

    @Test(expected = XmlParseException.class)
    public void testSyntaxBadEntity2() {
        XmlElement.parse("<a>&#^;</a>");
    }

    @Test(expected = XmlParseException.class)
    public void testUnsupportedEntity() {
        XmlElement.parse("<a>&pound;</a>");
    }

    @Test
    public void testSyntaxGoodEntity() {
        XmlElement.parse("<a>&#100;</a>");
    }

    @Test
    public void testComment2() {
        XmlElement x = XmlElement.parse("<a><b>boo</b><!-- hi there --></a>");
        assertEquals(1, x.countChildren());
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
    public void testChildrenWithName() {
        XmlElement x = XmlElement.parse("<a><b/><b/><c/></a>");
        assertEquals("a", x.name());
        assertEquals(2, x.childrenWithName("b").size());
    }

    @Test
    public void testHasTwoChildren() {
        String xml = "<a><b>boo</b><c step=\"large\">bingo</c><d>&amp;&gt;&lt;&quot;&apos;&#x7;&#x130;zz</d></a>";
        XmlElement x = XmlElement.parse(xml);
        assertEquals("a", x.name());
        assertTrue(x.hasChildren());
        assertEquals(3, x.countChildren());
        assertEquals(3, x.children().size());
        assertEquals("bingo", x.content("c"));
        assertTrue(x.attributeNames().isEmpty());
        assertEquals("b", x.firstChild().name());
        assertEquals("boo", x.firstChild().content());
        assertEquals("c", x.child("c").name());
        assertEquals("bingo", x.child(1).content());
        assertEquals("large", x.child("c").attribute("step"));
        assertEquals(
                "<a><b>boo</b><c step=\"large\">bingo</c><d>&amp;&gt;&lt;&quot;&apos;&#x7;&#x130;zz</d></a>",
                x.toString());
        assertEquals(0, x.lineNumber());
    }

    @Test(expected = NoSuchElementException.class)
    public void testNoChild() {
        XmlElement x = XmlElement.parse("<a/>");
        x.child("b");
    }

    @Test
    public void testComment() {
        XmlElement x = XmlElement.parse("<a><!-- hi there -->boo</a>");
        assertEquals("boo", x.content());
    }

    @Test
    public void testCData() {
        XmlElement x = XmlElement
                .parse("<a><![CDATA[\n" + "Within this Character Data block I can\n"
                        + "use double dashes as much as I want (along with <, &, ', and \")\n"
                        + "*and* %MyParamEntity; will be expanded to the text\n"
                        + "\"Has been expanded\" ... however, I can't use\n"
                        + "the CEND sequence. If I need to use CEND I must escape one of the\n"
                        + "brackets or the greater-than sign using concatenated CDATA sections.\n"
                        + "]]></a>");
        String s = x.content();
        assertTrue(s.contains("as I want (along with <, &, '"));
    }

}
