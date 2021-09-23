package com.github.davidmoten.aws.lw.client.xml.builder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XmlTest {

    @Test
    public void test() {
        String xml = Xml //
                .create("CompleteMultipartUpload") //
                .attribute("xmlns", "http://s3.amazonaws.com/doc/2006-03-01/") //
                .attribute("weird", "&<>'\"") //
                .element("Part") //
                .element("ETag").content("1234&") //
                .up() //
                .element("PartNumber").content("1") //
                .toString();
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<CompleteMultipartUpload weird=\"&amp;&lt;&gt;&apos;&quot;\" xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n"
                + "  <Part>\n"
                + "    <ETag>1234&amp;</ETag>\n"
                + "    <PartNumber>1</PartNumber>\n"
                + "  </Part>\n"
                + "</CompleteMultipartUpload>", xml);
    }

}
