package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.github.davidmoten.aws.lw.client.xml.builder.Xml;
import com.github.davidmoten.guavamini.Lists;
import com.github.davidmoten.junit.Asserts;

public class MultipartTest {

    private static byte[] createBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < 600000; i++) {
            bytes.write("0123456789".getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    @Test
    public void testMultipart() throws IOException {
        HttpClientTesting2 h = new HttpClientTesting2();
        Client s3 = Client //
                .s3() //
                .region("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .httpClient(h) //
                .build();
        h.add(startMultipartUpload());
        h.add(submitPart1());
        h.add(submitPart2Fails());
        h.add(submitPart2());
        h.add(completeMultipartUpload());

        try (MultipartOutputStream out = Multipart.s3(s3) //
                .bucket("mybucket") //
                .key("mykey") //
                .executor(Executors.newFixedThreadPool(1)) //
                .retryIntervalMs(1) //
                .partSizeMb(5) //
                .partTimeout(5, TimeUnit.MINUTES) //
                .outputStream()) {
            for (int i = 0; i < 600000; i++) {
                out.write("0123456789".getBytes(StandardCharsets.UTF_8));
            }
        }
        assertEquals(Arrays.asList( //
                "POST:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploads",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=1&uploadId=abcde",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=2&uploadId=abcde",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=2&uploadId=abcde",
                "POST:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploadId=abcde"), //
                h.urls());
    }

    @Test
    public void testMultipartAbort() throws IOException {
        HttpClientTesting2 h = new HttpClientTesting2();
        Client s3 = Client //
                .s3() //
                .region("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .httpClient(h) //
                .build();

        h.add(startMultipartUpload());
        h.add(submitPart1());
        h.add(submitPart2Fails());
        h.add(abortMultipartUpload());

        try (MultipartOutputStream out = Multipart.s3(s3) //
                .bucket("mybucket") //
                .key("mykey") //
                .executor(Executors.newFixedThreadPool(1)) //
                .maxAttemptsPerAction(1).retryIntervalMs(1) //
                .outputStream()) {
            for (int i = 0; i < 600000; i++) {
                out.write("0123456789".getBytes(StandardCharsets.UTF_8));
            }
        } catch (RuntimeException e) {
            assertTrue(e.getCause().getCause() instanceof MaxAttemptsExceededException);
        }
        
        assertEquals(Arrays.asList( //
                "POST:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploads",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=1&uploadId=abcde",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=2&uploadId=abcde",
                "DELETE:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploadId=abcde"), //
                h.urls());
    }

    private static Client s3() {
        return Client //
                .s3() //
                .region("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .build();
    }

    @SuppressWarnings("resource")
    @Test(expected = IllegalArgumentException.class)
    public void testMultipartOutputStreamBadArgumentPartTimeoutMs() {
        new MultipartOutputStream(s3(), "bucket", "key", x -> x, Executors.newFixedThreadPool(1),
                -1, 0, 0, 0);
    }

    @SuppressWarnings("resource")
    @Test(expected = IllegalArgumentException.class)
    public void testMultipartOutputStreamBadArgumentMaxAttempts() {
        new MultipartOutputStream(s3(), "bucket", "key", x -> x, Executors.newFixedThreadPool(1), 1,
                0, 0, 0);
    }

    @SuppressWarnings("resource")
    @Test(expected = IllegalArgumentException.class)
    public void testMultipartOutputStreamBadArgumentRetryIntervalMs() {
        new MultipartOutputStream(s3(), "bucket", "key", x -> x, Executors.newFixedThreadPool(1), 1,
                1, -1, 0);
    }

    @SuppressWarnings("resource")
    @Test(expected = IllegalArgumentException.class)
    public void testMultipartOutputStreamBadArgumentPartSize() {
        new MultipartOutputStream(s3(), "bucket", "key", x -> x, Executors.newFixedThreadPool(1), 1,
                1, 1, 1000);
    }

    @Test
    public void isUtilityClass() {
        Asserts.assertIsUtilityClass(Multipart.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMultipartBadArgument() {
        Multipart.s3(s3()).bucket("bucket").key("key").maxAttemptsPerAction(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMultipartBadArgument2() {
        Multipart.s3(s3()).bucket("bucket").key("key").partSize(1000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMultipartBadArgument3() {
        Multipart.s3(s3()).bucket("bucket").key("key").retryIntervalMs(-1);
    }

    private static final Closeable DO_NOTHING = () -> {
    };

    private static InputStream emptyInputStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private static ResponseInputStream completeMultipartUpload() {
        // response for completion
        // actually includes xml response but we don't read it
        // so we don't simulate it
        Map<String, List<String>> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Length", Arrays.asList("0"));
        return new ResponseInputStream(DO_NOTHING, 200, responseHeaders, emptyInputStream());
    }

    private static ResponseInputStream abortMultipartUpload() {
        // response for completion
        // actually includes xml response but we don't read it
        // so we don't simulate it
        Map<String, List<String>> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Length", Arrays.asList("0"));
        return new ResponseInputStream(DO_NOTHING, 200, responseHeaders, emptyInputStream());
    }

    private static ResponseInputStream submitPart2() {
        // response for submit part 2
        Map<String, List<String>> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Length", Arrays.asList("0"));
        responseHeaders.put("ETag", Arrays.asList("\"etag2\""));
        return new ResponseInputStream(DO_NOTHING, 200, responseHeaders, emptyInputStream());
    }

    private static ResponseInputStream submitPart2Fails() {
        // response for submit part 2 - fails
        Map<String, List<String>> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Length", Arrays.asList("0"));
        responseHeaders.put("ETag", Arrays.asList("\"etag2\""));
        return new ResponseInputStream(DO_NOTHING, 500, responseHeaders, emptyInputStream());
    }

    private static ResponseInputStream submitPart1() {
        // response for submit part 1
        Map<String, List<String>> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Length", Arrays.asList("0"));
        responseHeaders.put("ETag", Arrays.asList("\"etag1\""));
        return new ResponseInputStream(DO_NOTHING, 200, responseHeaders, emptyInputStream());
    }

    private static ResponseInputStream startMultipartUpload() {
        String responseXml = Xml.create("InitiateMultipartUploadResult") //
                .a("xmlns", "http://s3.amazonaws.com/doc/2006-03-01/") //
                .e("Bucket").content("mybucket") //
                .up() //
                .e("Key").content("mykey") //
                .up() //
                .e("UploadId").content("abcde") //
                .toString();
        byte[] bytes = responseXml.getBytes(StandardCharsets.UTF_8);
        Map<String, List<String>> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Length", Arrays.asList("" + bytes.length));
        InputStream result = new ByteArrayInputStream(responseXml.getBytes(StandardCharsets.UTF_8));
        return new ResponseInputStream(DO_NOTHING, 200, responseHeaders, result);
    }
}
