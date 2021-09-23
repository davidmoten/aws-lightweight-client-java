package com.github.davidmoten.aws.lw.client.xml.builder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XmlTest {

    @Test
    public void test() {
        String xml = Xml //
                .root("CompleteMultipartUpload") //
                .attribute("xmlns", "http://s3.amazonaws.com/doc/2006-03-01/") //
                .element("Part") //
                .element("ETag").content("1234") //
                .up() //
                .element("PartNumber").content("1") //
                .toString();
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<CompleteMultipartUpload xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n"
                + "  <Part>\n"
                + "    <ETag>1234</ETag>\n"
                + "    <PartNumber>1</PartNumber>\n"
                + "  </Part>\n"
                + "</CompleteMultipartUpload>", xml);
    }

}
