package com.github.davidmoten.aws.lw.client.xml.builder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XmlTest {

    @Test
    public void test() {
        String xml = Xml //
                .create("CompleteMultipartUpload") //
                .a("xmlns", "http://s3.amazonaws.com/doc/2006-03-01/") //
                .a("weird", "&<>'\"") //
                .e("Part") //
                .e("ETag").content("1234&") //
                .up() //
                .e("PartNumber").content("1") //
                .toString();
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<CompleteMultipartUpload weird=\"&amp;&lt;&gt;&apos;&quot;\" xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n"
                + "  <Part>\n" + "    <ETag>1234&amp;</ETag>\n" + "    <PartNumber>1</PartNumber>\n"
                + "  </Part>\n" + "</CompleteMultipartUpload>", xml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testContentAndChild() {
        Xml.create("root").content("boo").element("child");
    }

    @Test
    public void testPrelude() {
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<root>\n" + "</root>",
                Xml.create("root").toString());
    }

    @Test
    public void testNoPrelude() {
        assertEquals("<root>\n" + "</root>", Xml.create("root").excludePrelude().toString());
    }

    @Test
    public void testNoPreludeOnChild() {
        assertEquals("<root>\n  <thing></thing>\n" + "</root>",
                Xml.create("root").element("thing").content("").excludePrelude().toString());
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testNullName() {
        Xml.create(null);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testBlankName() {
        Xml.create("  ");
    }
    
    @Test
    public void testUnusualCharacters1() {
        assertEquals(
                "<root>&#x10461;bc</root>", Xml.create("root").excludePrelude().content("" + (char) 0xd801 + "abc").toString());
        assertEquals(
                "<root>&#xfffd;</root>", Xml.create("root").excludePrelude().content("" + (char) 0xd801).toString());
    }
    
    @Test
    public void testUnusualCharacters2() {
        assertEquals(
                "<root>&#xfffd;abc</root>", Xml.create("root").excludePrelude().content("" + (char) 0xdc00 + "abc").toString());
    }
    
    @Test
    public void testIllegalCharacters() {
        assertEquals(
                "<root>&#xfffd;abc</root>", Xml.create("root").excludePrelude().content("" + (char) 0x00 + "abc").toString());
    }
    
    @Test
    public void testLegalWhitespace() {
        assertEquals(
                "<root>\t\n\rabc</root>", Xml.create("root").excludePrelude().content("\t\n\rabc").toString());
    }
    
    @Test
    public void testUnusualCharacters3() {
        assertEquals(
                "<root>&#x103ff;&#xfffd;&#xfffd;&#xefff;&#xd7ff;</root>", Xml.create("root").excludePrelude().content("" + (char) 0xd800  + (char) 0xdfff  + (char)0xfffe + (char) 0xffff + (char) 0xefff + (char) 0xd7ff).toString());
    }


}
