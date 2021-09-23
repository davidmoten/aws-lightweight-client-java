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


}
