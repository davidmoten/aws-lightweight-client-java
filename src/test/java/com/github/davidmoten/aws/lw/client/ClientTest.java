package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

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
                .accessKey("123") //
                .secretKey("456") //
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
                .connectTimeout(5, TimeUnit.SECONDS) //
                .readTimeout(6, TimeUnit.SECONDS) //
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

    @Test(expected = IllegalArgumentException.class)
    public void testBadConnectTimeout() {
        Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .connectTimeout(-1, TimeUnit.SECONDS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadReadTimeout() {
        Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .readTimeout(-1, TimeUnit.SECONDS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadConnectTimeout2() {
        s3.path().connectTimeout(-1, TimeUnit.SECONDS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadReadTimeout2() {
        s3.path().readTimeout(-1, TimeUnit.SECONDS);
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
    public void testDefaultClientFromEnvironment() {
        Map<String, String> map = new HashMap<>();
        map.put("AWS_REGION", "ap-southeast-2");
        map.put("AWS_ACCESS_KEY_ID", "123");
        map.put("AWS_SECRET_ACCESS_KEY", "abc");
        Client client = Client.s3().environment(name -> map.get(name)).defaultClient().build();
        assertEquals("ap-southeast-2", client.regionName());
        Credentials c = client.credentials();
        assertEquals("123", c.accessKey());
        assertEquals("abc", c.secretKey());
        assertFalse(c.sessionToken().isPresent());
    }

    @Test
    public void testDefaultClientFromSystemProperties() {
        System.setProperty("aws.accessKeyId", "123");
        System.setProperty("aws.secretKey", "abc");
        Client client = Client.s3().regionName("ap-southeast-2").credentialsFromSystemProperties()
                .build();
        assertEquals("ap-southeast-2", client.regionName());
        Credentials c = client.credentials();
        assertEquals("123", c.accessKey());
        assertEquals("abc", c.secretKey());
        assertFalse(c.sessionToken().isPresent());
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
        assertEquals("https://blah", hc.endpointUrl.toString());
    }

    @Test
    public void testAttribute() {
        s3.attribute("colour", "blue") //
                .attribute("color", "green") //
                .method(HttpMethod.PUT) //
                .execute();
        assertEquals(
                "https://s3.ap-southeast-2.amazonaws.com/?Attribute.1.Name=colour&Attribute.1.Value=blue&Attribute.2.Name=color&Attribute.2.Value=green",
                hc.endpointUrl.toString());
    }

    @Test
    public void testAttributePrefix() {
        s3.attribute("colour", "blue") //
                .attribute("color", "green") //
                .attributePrefix("surface") //
                .attribute("texture", "rough") //
                .method(HttpMethod.PUT) //
                .execute();
        assertEquals(
                "https://s3.ap-southeast-2.amazonaws.com/?Attribute.1.Name=colour&Attribute.1.Value=blue&Attribute.2.Name=color&Attribute.2.Value=green&surface.1.Name=texture&surface.1.Value=rough",
                hc.endpointUrl.toString());
    }

    @Test
    public void testAttributePrefix2() {
        s3.attributePrefix("surface") //
                .attribute("color", "green") //
                .method(HttpMethod.PUT) //
                .execute();
        assertEquals(
                "https://s3.ap-southeast-2.amazonaws.com/?surface.1.Name=color&surface.1.Value=green",
                hc.endpointUrl.toString());
    }

    @Test
    public void testServerOkResponse() throws IOException {
        Client client = Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .clock(() -> 1622695846902L) //
                .build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("<a>hello</a>").setResponseCode(200));
            String baseUrl = server.url("").toString();
            String text = client.url(baseUrl) //
                    .requestBody("hi there") //
                    .responseAsXml() //
                    .content(); //
            assertEquals("hello", text);
        }
    }

    @Test
    public void testServerErrorResponse() throws IOException {
        Client client = Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .clock(() -> 1622695846902L) //
                .build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("hello").setResponseCode(500));
            String baseUrl = server.url("").toString();
            try {
                client.url(baseUrl) //
                        .requestBody("hi there") //
                        .responseAsUtf8(); //
                Assert.fail();
            } catch (ServiceException e) {
                assertEquals(500, e.statusCode());
                assertEquals("hello", e.message());
            }
        }
    }

    @Test
    public void testServerErrorCustomExceptions() throws IOException {
        Client client = Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .clock(() -> 1622695846902L) //
                .exception(r -> !r.isOk(), r -> new UnsupportedOperationException()) //
                .build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("hello").setResponseCode(500));
            String baseUrl = server.url("").toString();
            try {
                client.url(baseUrl) //
                        .requestBody("hi there") //
                        .responseAsUtf8(); //
                Assert.fail();
            } catch (UnsupportedOperationException e) {
                // all good
            }
        }
    }

    @Test
    public void testServerErrorCustomExceptionsPassThrough() throws IOException {
        Client client = Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .clock(() -> 1622695846902L) //
                .exception(r -> !r.isOk() && r.statusCode() == 404,
                        r -> new UnsupportedOperationException()) //
                .build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("hello").setResponseCode(500));
            String baseUrl = server.url("").toString();
            try {
                client.url(baseUrl) //
                        .requestBody("hi there") //
                        .responseAsUtf8(); //
                Assert.fail();
            } catch (ServiceException e) {
                // all good
            }
        }
    }

    @Test
    public void testServerErrorExceptionFactory() throws IOException {
        Client client = Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .clock(() -> 1622695846902L) //
                .exceptionFactory(r -> {
                    if (r.isOk()) {
                        return Optional.empty();
                    } else {
                        return Optional.of(new UnsupportedOperationException());
                    }
                }) //
                .build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("hello").setResponseCode(500));
            String baseUrl = server.url("").toString();
            try {
                client.url(baseUrl) //
                        .requestBody("hi there") //
                        .responseAsUtf8(); //
                Assert.fail();
            } catch (UnsupportedOperationException e) {
                // all good
            }
        }
    }

    @Test
    public void testWithServerNoResponseBody() throws IOException {
        Client client = Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .clock(() -> 1622695846902L) //
                .build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200));
            String baseUrl = server.url("").toString();
            String text = client.url(baseUrl) //
                    .method(HttpMethod.PUT) //
                    .requestBody("hi there") //
                    .responseAsUtf8(); //
            assertEquals("", text);
        }
    }

    @Test
    public void testPresignedUrlWithRequestBody() {
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
    public void testPresignedUrlWithoutRequestBody() {
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
                .regionName("ap-southeast-2") //
                .presignedUrl(5, TimeUnit.DAYS);
        assertEquals(
                "https://s3.ap-southeast-2.amazonaws.com/MyBucket?type=thing&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=123/20210603/ap-southeast-2/s3/aws4_request&X-Amz-Date=20210603T045046Z&X-Amz-Expires=432000&X-Amz-SignedHeaders=host;x-amz-content-sha256&X-Amz-Signature=b14df0b38e6a1dadce2f340483bd61db69730da26d571e1f0cfacea993372085",
                presignedUrl);
    }

    @Test
    public void testPresignedUrlWhenUrlHasPort() {
        Client client = Client //
                .s3() //
                .regionName("us-west-1") //
                .accessKey("123") //
                .secretKey("456") //
                .clock(() -> 1622695846902L) //
                .build();
        // create a bucket
        String presignedUrl = client //
                .url("https://s3.myserver.com:8443") //
                .method(HttpMethod.PUT) //
                .regionName("ap-southeast-2") //
                .presignedUrl(5, TimeUnit.DAYS);
        assertEquals(
                "https://s3.myserver.com:8443?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=123/20210603/ap-southeast-2/s3/aws4_request&X-Amz-Date=20210603T045046Z&X-Amz-Expires=432000&X-Amz-SignedHeaders=host;x-amz-content-sha256&X-Amz-Signature=e78feecf8da1d5c8029f117bb8bd10779d420ce02e838461e3abfafe7d565a5c",
                presignedUrl);
    }

    @Test
    public void testPresignedUrlWithoutQueryParameters() {
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
                .method(HttpMethod.PUT) //
                .regionName("ap-southeast-2") //
                .presignedUrl(5, TimeUnit.DAYS);
        assertEquals(
                "https://s3.ap-southeast-2.amazonaws.com/MyBucket?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=123/20210603/ap-southeast-2/s3/aws4_request&X-Amz-Date=20210603T045046Z&X-Amz-Expires=432000&X-Amz-SignedHeaders=host;x-amz-content-sha256&X-Amz-Signature=4ffc22f4b86b0514a29994c92bbf0342e2dccc66cdceae670414c847baa338ef",
                presignedUrl);
    }

    @Test
    public void testAuthorizationSignedRequest() {
        Client s3 = Client //
                .s3() //
                .regionName("ap-southeast-2") //
                .credentials(Credentials.of("123", "456", "789")).clock(() -> 1622695846902L) //
                .httpClient(hc) //
                .build();

        s3 //
                .path("myBucket/myObject.txt") //
                .query("Type", "Thing") //
                .header("my-header", "blah") //
                .header("my-header", "blah2") //
                .requestBody("something") //
                .response();

        assertEquals("GET", hc.httpMethod);
        assertEquals("something", hc.requestBodyString());
        assertEquals("https://s3.ap-southeast-2.amazonaws.com/myBucket/myObject.txt?Type=Thing",
                hc.endpointUrl.toString());
        Map<String, String> a = new HashMap<>();
        a.put("my-header", "blah,blah2");
        a.put("x-amz-content-sha256",
                "3fc9b689459d738f8c88a3a48aa9e33542016b7a4052e001aaa536fca74813cb");
        a.put("Authorization",
                "AWS4-HMAC-SHA256 Credential=123/20210603/ap-southeast-2/s3/aws4_request, SignedHeaders=content-length;host;my-header;x-amz-content-sha256;x-amz-date;x-amz-security-token, Signature=72983b3d44575f7b8fea5dd7148a764a7031122154387a30764c56d171906c80");
        a.put("Host", "s3.ap-southeast-2.amazonaws.com");
        a.put("x-amz-date", "20210603T045046Z");
        a.put("content-length", "" + 9);
        a.put("x-amz-security-token", "789");
        for (Entry<String, String> entry : hc.headers.entrySet()) {
            System.out.println(entry.getKey());
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
