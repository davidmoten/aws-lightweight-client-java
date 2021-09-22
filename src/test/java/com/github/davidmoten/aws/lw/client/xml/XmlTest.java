package com.github.davidmoten.aws.lw.client.xml;

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
        System.out.println(xml);
    }

}
