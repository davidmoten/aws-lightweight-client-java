package com.github.davidmoten.aws.lw.client.internal.auth;

import static com.github.davidmoten.aws.lw.client.internal.auth.AwsSignatureVersion4.getCanonicalizedResourcePath;
import static org.junit.Assert.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;

public class AwsSignatureVersion4Test {
    
    @Test
    public void testPath() throws MalformedURLException {
        assertEquals("/", getCanonicalizedResourcePath(new URL("https://")));
    }
    
    @Test
    public void testPath2() throws MalformedURLException {
        assertEquals("/hi", getCanonicalizedResourcePath(new URL("https://blah.com/hi")));
    }
    
    @Test(expected=RuntimeException.class)
    public void testSignBadAlgorithmThrows() {
        AwsSignatureVersion4.sign("hi there", new byte[] {1,2,3,4}, "doesnotexist");
    }
    
    @Test(expected=RuntimeException.class)
    public void testSignBadKeyThrows2() {
        AwsSignatureVersion4.sign("hi there", new byte[] {}, AwsSignatureVersion4.ALGORITHM_HMAC_SHA256);
    }

}
