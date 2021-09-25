package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.junit.Test;

import com.github.davidmoten.aws.lw.client.xml.builder.Xml;

public class MultipartTest {

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
                .outputStream()) {
            for (int i = 0; i < 600000; i++) {
                out.write("0123456789".getBytes(StandardCharsets.UTF_8));
            }
        }
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
        h.add(submitPart2());
        h.add(completeMultipartUpload());

        try (MultipartOutputStream out = Multipart.s3(s3) //
                .bucket("mybucket") //
                .key("mykey") //
                .executor(Executors.newFixedThreadPool(1)) //
                .maxAttemptsPerAction(1).retryIntervalMs(1) //
                .outputStream()) {
            for (int i = 0; i < 600000; i++) {
                out.write("0123456789".getBytes(StandardCharsets.UTF_8));
            }
        } catch( RuntimeException e) {
            assertTrue(e.getCause().getCause() instanceof MaxAttemptsExceededException);
        }
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
