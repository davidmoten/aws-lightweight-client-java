package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClientTest {

    private static final HttpClientTesting hc = HttpClientTesting.INSTANCE;

    @Test
    public void test() {
        Client client = Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123").secretKey("456") //
                .httpClient(hc) //
                .build();
        // create a bucket
        client //
                .path("MyBucket") //
                .metadata("category", "something") //
                .method(HttpMethod.PUT) //
                .requestBody("hi there") //
                .connectTimeoutMs(5000) //
                .readTimeoutMs(6000) //
                .execute();
        assertEquals("https://s3.ap-southeast-2.amazonaws.com/MyBucket", hc.endpointUrl.toString());
        assertEquals("PUT", hc.httpMethod);
        assertEquals("9b96a1fe1d548cbbc960cc6a0286668fd74a763667b06366fb2324269fcabaa4",
                hc.headers.get("x-amz-content-sha256"));
        String authorization = hc.headers.get("Authorization");
        assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 Credential="));
        assertTrue(authorization.contains("/ap-southeast-2/s3/aws4_request, SignedHeaders=content-length;host;x-amz-content-sha256;x-amz-date;x-amz-meta-category"));
        assertEquals("8", hc.headers.get("content-length"));
        assertEquals("s3.ap-southeast-2.amazonaws.com", hc.headers.get("Host"));
        assertTrue(hc.headers.get("x-amz-date").endsWith("Z"));
        assertEquals("something", hc.headers.get("x-amz-meta-category"));

        assertEquals("hi there", hc.requestBodyString());
        assertEquals(5000, hc.connectTimeoutMs);
        assertEquals(6000, hc.readTimeoutMs);
    }

}
