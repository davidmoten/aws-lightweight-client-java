package com.github.davidmoten.aws.lw.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.davidmoten.kool.function.Consumer;
import org.junit.Test;

import com.github.davidmoten.aws.lw.client.internal.Retries;
import com.github.davidmoten.aws.lw.client.xml.builder.Xml;
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
    public void testMultipart() throws Exception {
        testMultipart(out -> {
            for (int i = 0; i < 600000; i++) {
                out.write("0123456789".getBytes(StandardCharsets.UTF_8));
            }
        });
    }

    @Test
    public void testMultipartSingleWrite() throws Exception {
        testMultipart(out -> {
            out.write(createBytes());
        });
    }

    @Test
    public void testMultipartSameChunkAsPartSize() throws Exception {
        testMultipart(out -> {
            byte[] bytes = createBytes();
            int partSize = 5 * 1024 * 1024;
            out.write(bytes, 0, partSize);
            out.write(bytes, partSize, bytes.length - partSize);
        });
    }

    @Test
    public void testMultipartSingleWriteOfExactlyPartsSize() throws Exception {
        HttpClientTestingWithQueue h = new HttpClientTestingWithQueue();
        Client s3 = Client //
                .s3() //
                .region("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .httpClient(h) //
                .build();
        h.add(startMultipartUpload());
        h.add(submitPart1());
        h.add(completeMultipartUpload());

        try (MultipartOutputStream out = Multipart.s3(s3) //
                .bucket("mybucket") //
                .key("mykey") //
                .executor(Executors.newFixedThreadPool(1)) //
                .retryInitialInterval(1, TimeUnit.MILLISECONDS) //
                .transformCreateRequest(x -> x) //
                .partSizeMb(5) // s
                .partTimeout(5, TimeUnit.MINUTES) //
                .outputStream()) {
            out.write(new byte[5 * 1024 * 1024]);
        }
        assertEquals(Arrays.asList( //
                "POST:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploads",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=1&uploadId=abcde",
                "POST:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploadId=abcde"), //
                h.urls());
    }

    public void testMultipart(Consumer<MultipartOutputStream> consumer) throws Exception {
        HttpClientTestingWithQueue h = new HttpClientTestingWithQueue();
        Client s3 = Client //
                .s3() //
                .region("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .httpClient(h) //
                .baseUrlFactory((serviceName, region) //
                        -> "https://"+ serviceName + "." + region.map(x -> x + ".").orElse("") + "mine.com/") //
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
                .retryInitialInterval(1, TimeUnit.MILLISECONDS) //
                .partSizeMb(5) //
                .partTimeout(5, TimeUnit.MINUTES) //
                .outputStream()) {
            consumer.accept(out);
        }
        assertEquals(Arrays.asList( //
                "POST:https://s3.ap-southeast-2.mine.com/mybucket/mykey?uploads",
                "PUT:https://s3.ap-southeast-2.mine.com/mybucket/mykey?partNumber=1&uploadId=abcde",
                "PUT:https://s3.ap-southeast-2.mine.com/mybucket/mykey?partNumber=2&uploadId=abcde",
                "PUT:https://s3.ap-southeast-2.mine.com/mybucket/mykey?partNumber=2&uploadId=abcde",
                "POST:https://s3.ap-southeast-2.mine.com/mybucket/mykey?uploadId=abcde"), //
                h.urls());
        assertArrayEquals(createBytes(), h.bytes());
    }

    @Test(expected = UncheckedIOException.class)
    public void testMultipartUploadFileDoesNotExist() throws IOException {
        HttpClientTestingWithQueue h = new HttpClientTestingWithQueue();
        Client s3 = Client //
                .s3() //
                .region("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .httpClient(h) //
                .build();
        Multipart.s3(s3) //
                .bucket("mybucket") //
                .key("mykey") //
                .executor(Executors.newFixedThreadPool(1)) //
                .retryInitialInterval(1, TimeUnit.MILLISECONDS) //
                .partSizeMb(5) //
                .partTimeout(5, TimeUnit.MINUTES) //
                .upload(new File("target/doesnotexist"));

    }

    @Test(expected = RuntimeException.class)
    public void testMultipartUploadInputStreamFactoryThrows() throws IOException {
        HttpClientTestingWithQueue h = new HttpClientTestingWithQueue();
        Client s3 = Client //
                .s3() //
                .region("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .httpClient(h) //
                .build();
        Multipart.s3(s3) //
                .bucket("mybucket") //
                .key("mykey") //
                .executor(Executors.newFixedThreadPool(1)) //
                .retryInitialInterval(1, TimeUnit.MILLISECONDS) //
                .partSizeMb(5) //
                .partTimeout(5, TimeUnit.MINUTES) //
                .upload(() -> {
                    throw new Exception();
                });
    }

    @Test
    public void testMultipartUploadFile() throws IOException {
        HttpClientTestingWithQueue h = new HttpClientTestingWithQueue();
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

        File file = new File("target/temp.txt");
        byte[] bytes = createBytes();
        Files.write(file.toPath(), bytes);
        Multipart.s3(s3) //
                .bucket("mybucket") //
                .key("mykey") //
                .executor(Executors.newFixedThreadPool(1)) //
                .retryInitialInterval(1, TimeUnit.MILLISECONDS) //
                .partSizeMb(5) //
                .partTimeout(5, TimeUnit.MINUTES) //
                .upload(file);

        assertEquals(Arrays.asList( //
                "POST:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploads",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=1&uploadId=abcde",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=2&uploadId=abcde",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=2&uploadId=abcde",
                "POST:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploadId=abcde"), //
                h.urls());
        assertArrayEquals(bytes, h.bytes());
    }

    @Test
    public void testMultipartUploadByteArray() throws IOException {
        HttpClientTestingWithQueue h = new HttpClientTestingWithQueue();
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

        byte[] bytes = createBytes();
        Multipart.s3(s3) //
                .bucket("mybucket") //
                .key("mykey") //
                .executor(Executors.newFixedThreadPool(1)) //
                .retryInitialInterval(1, TimeUnit.MILLISECONDS) //
                .partSizeMb(5) //
                .partTimeout(5, TimeUnit.MINUTES) //
                .upload(bytes);

        assertEquals(Arrays.asList( //
                "POST:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploads",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=1&uploadId=abcde",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=2&uploadId=abcde",
                "PUT:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?partNumber=2&uploadId=abcde",
                "POST:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploadId=abcde"), //
                h.urls());
        assertArrayEquals(bytes, h.bytes());
    }

    @Test
    public void testMultipartAbort() throws IOException {
        HttpClientTestingWithQueue h = new HttpClientTestingWithQueue();
        Client s3 = Client //
                .s3() //
                .region("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .httpClient(h) //
                .retryMaxAttempts(1) //
                .build();

        h.add(startMultipartUpload());
        h.add(submitPart1());
        h.add(submitPart2Fails());
        h.add(abortMultipartUpload());

        try (MultipartOutputStream out = Multipart.s3(s3) //
                .bucket("mybucket") //
                .key("mykey") //
                .executor(Executors.newFixedThreadPool(1)) //
                .maxAttemptsPerAction(1) //
                .retryInitialInterval(1, TimeUnit.SECONDS) //
                .retryBackoffFactor(1.0) //
                .retryMaxInterval(10, TimeUnit.SECONDS) //
                .outputStream()) {
            for (int i = 0; i < 600000; i++) {
                out.write("0123456789".getBytes(StandardCharsets.UTF_8));
            }
        } catch (RuntimeException e) {
            assertTrue(e.getCause().getCause() instanceof ServiceException);
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
                -1, Retries.create(x -> false, x -> true), 0);
    }

    @SuppressWarnings("resource")
    @Test(expected = IllegalArgumentException.class)
    public void testMultipartOutputStreamBadArgumentMaxAttempts() {
        new MultipartOutputStream(s3(), "bucket", "key", x -> x, Executors.newFixedThreadPool(1), 1,
                Retries.create(x -> false, x -> true), 0);
    }

    @SuppressWarnings("resource")
    @Test(expected = IllegalArgumentException.class)
    public void testMultipartOutputStreamBadArgumentPartSize() {
        new MultipartOutputStream(s3(), "bucket", "key", x -> x, Executors.newFixedThreadPool(1), 1,
                Retries.create(x -> false, x -> true), 1000);
    }

    @Test
    public void testMultipartDefaultExecutor() {
        HttpClientTestingWithQueue h = new HttpClientTestingWithQueue();
        Client s3 = Client //
                .s3() //
                .region("ap-southeast-2") //
                .accessKey("123") //
                .secretKey("456") //
                .httpClient(h) //
                .build();
        h.add(startMultipartUpload());

        Multipart.s3(s3) //
                .bucket("mybucket") //
                .key("mykey") //
                .outputStream();

        assertEquals(Arrays.asList( //
                "POST:https://s3.ap-southeast-2.amazonaws.com/mybucket/mykey?uploads"), h.urls());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMultipartBadPartTimeout() {
        Multipart.s3(s3()) //
                .bucket("mybucket") //
                .key("mykey") //
                .partTimeout(-1, TimeUnit.MINUTES);
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
        Multipart.s3(s3()).bucket("bucket").key("key").retryInitialInterval(-1, TimeUnit.MILLISECONDS);
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
