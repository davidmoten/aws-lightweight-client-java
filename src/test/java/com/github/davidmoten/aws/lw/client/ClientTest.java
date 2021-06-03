package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ClientTest {

    private static final HttpClientTesting hc = HttpClientTesting.INSTANCE;

    private static final Client s3 = Client //
            .s3() //
            .regionName("ap-southeast-2") //
            .accessKey("123") //
            .secretKey("456") //
            .httpClient(hc) //
            .build();

    @Test
    public void test() {
        Client client = Client //
                .s3() //
                .regionName("us-west-1") //
                .accessKey("123").secretKey("456") //
                .httpClient(hc) //
                .build();
        // create a bucket
        client //
                .path("MyBucket") //
                .metadata("category", "something") //
                .query("type", "thing") //
                .attribute("color", "red") //
                .attribute("color", "blue") //
                .attributePrefix("Message") //
                .attribute("name", "hi") //
                .attribute("name", "there") //
                .method(HttpMethod.PUT) //
                .requestBody("hi there") //
                .regionName("ap-southeast-2") //
                .connectTimeoutMs(5000) //
                .readTimeoutMs(6000) //
                .execute();
        assertEquals(
                "https://s3.ap-southeast-2.amazonaws.com/MyBucket?type=thing&Attribute.1.Name=color&Attribute.1.Value=red&Attribute.2.Name=color&Attribute.2.Value=blue&Message.1.Name=name&Message.1.Value=hi&Message.2.Name=name&Message.2.Value=there",
                hc.endpointUrl.toString());
        assertEquals("PUT", hc.httpMethod);
        assertEquals("9b96a1fe1d548cbbc960cc6a0286668fd74a763667b06366fb2324269fcabaa4",
                hc.headers.get("x-amz-content-sha256"));
        String authorization = hc.headers.get("Authorization");
        assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 Credential="));
        assertTrue(authorization.contains(
                "/ap-southeast-2/s3/aws4_request, SignedHeaders=content-length;host;x-amz-content-sha256;x-amz-date;x-amz-meta-category"));
        assertEquals("8", hc.headers.get("content-length"));
        assertEquals("s3.ap-southeast-2.amazonaws.com", hc.headers.get("Host"));
        assertTrue(hc.headers.get("x-amz-date").endsWith("Z"));
        assertEquals("something", hc.headers.get("x-amz-meta-category"));
        assertEquals("hi there", hc.requestBodyString());
        assertEquals(5000, hc.connectTimeoutMs);
        assertEquals(6000, hc.readTimeoutMs);
    }

    @Test
    public void testTimeoutsAtClientLevel() {
        Client client = Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .connectTimeout(5, TimeUnit.SECONDS) //
                .readTimeout(6, TimeUnit.SECONDS) //
                .httpClient(hc) //
                .build();

        // create a bucket
        client //
                .path("MyBucket") //
                .method(HttpMethod.PUT) //
                .requestBody("hi there") //
                .execute();

        assertEquals(5000, hc.connectTimeoutMs);
        assertEquals(6000, hc.readTimeoutMs);
    }

    @Test
    public void testNoPathOrUrlSet() {
        s3.query("number", "four") //
                .method(HttpMethod.PUT) //
                .execute();
        assertEquals("https://s3.ap-southeast-2.amazonaws.com/?number=four",
                hc.endpointUrl.toString());
    }
    
    @Test
    public void testUrlSet() {
        s3.url("https://blah") //
                .method(HttpMethod.PUT) //
                .execute();
        assertEquals("https://blah",
                hc.endpointUrl.toString());
    }

    @Test
    public void testPresignedUrl() {
        Client client = Client //
                .s3() //
                .regionName("us-west-1") //
                .accessKey("123") //
                .secretKey("456") //
                .clock(() -> 1622695846902L) //
                .build();
        // create a bucket
        String presignedUrl = client //
                .path("MyBucket") //
                .query("type", "thing") //
                .method(HttpMethod.PUT) //
                .requestBody("hi there") //
                .regionName("ap-southeast-2") //
                .presignedUrl(5, TimeUnit.DAYS);
        assertEquals(
                "https://s3.ap-southeast-2.amazonaws.com/MyBucket?type=thing&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=123/20210603/ap-southeast-2/s3/aws4_request&X-Amz-Date=20210603T045046Z&X-Amz-Expires=432000&X-Amz-SignedHeaders=content-length;host;x-amz-content-sha256&X-Amz-Signature=3f27d3fe5e595d787990866d05112cd73e21be2275bf02269b640bc9b7c35ec6",
                presignedUrl);
    }

    @Test
    public void testAuthorizationSignedRequest() {
        Client s3 = Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .clock(() -> 1622695846902L) //
                .httpClient(hc) //
                .build();

        s3 //
                .path("myBucket/myObject.txt") //
                .query("Type", "Thing") //
                .header("my-header", "blah") //
                .requestBody("something") //
                .response();

        assertEquals("GET", hc.httpMethod);
        assertEquals("something", hc.requestBodyString());
        assertEquals("https://s3.ap-southeast-2.amazonaws.com/myBucket/myObject.txt?Type=Thing",
                hc.endpointUrl.toString());
//        hc.headers.entrySet().forEach(System.out::println);
        Map<String, String> a = new HashMap<>();
        a.put("my-header", "blah");
        a.put("x-amz-content-sha256",
                "3fc9b689459d738f8c88a3a48aa9e33542016b7a4052e001aaa536fca74813cb");
        a.put("Authorization",
                "AWS4-HMAC-SHA256 Credential=123/20210603/ap-southeast-2/s3/aws4_request, SignedHeaders=content-length;host;my-header;x-amz-content-sha256;x-amz-date, Signature=b68fd6e232d7a0b694fc4b7b366e2ed887c7ba976c16dd5ccfcf77a0fae8c9e7");
        a.put("Host", "s3.ap-southeast-2.amazonaws.com");
        a.put("x-amz-date", "20210603T045046Z");
        a.put("content-length", "" + 9);
        for (Entry<String, String> entry : hc.headers.entrySet()) {
            assertEquals(a.get(entry.getKey()), entry.getValue());
        }
        assertEquals(a.size(), hc.headers.size());
    }

    @Test
    public void testOtherServiceNames() {
        Client s3 = Client.s3().regionName("ap-southeast-2").accessKey("123").secretKey("abc")
                .build();
        assertEquals("iam", Client.iam().from(s3).build().serviceName());
        assertEquals("ec2", Client.ec2().from(s3).build().serviceName());
        assertEquals("lambda", Client.lambda().from(s3).build().serviceName());
        assertEquals("s3", Client.s3().from(s3).build().serviceName());
        assertEquals("sns", Client.sns().from(s3).build().serviceName());
        assertEquals("sqs", Client.sqs().from(s3).build().serviceName());
    }

}
