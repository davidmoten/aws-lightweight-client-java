package com.github.davidmoten.aws.lw.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.github.davidmoten.aws.lw.client.internal.util.Preconditions;
import com.github.davidmoten.aws.lw.client.xml.builder.Xml;

public final class S3MultipartOutputStream extends OutputStream {

    private static final int THRESHOLD = 5 * 1024 * 1024;

    private final Client s3;
    private final String bucket;
    private final String key;
    private final String uploadId;
    private final ExecutorService executor;
    private final ByteArrayOutputStream bytes;
    private final List<String> etags;
    private final byte[] singleByte = new byte[1]; // for reuse in write(int) method
    private final long timeoutMs;
    private int nextPart = 1;

    private S3MultipartOutputStream(Client s3, String bucket, String key,
            Function<? super Request, ? extends Request> createTransform, ExecutorService executor,
            long timeoutMs) {
        this.s3 = s3;
        this.bucket = bucket;
        this.key = key;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
        this.bytes = new ByteArrayOutputStream();
        this.etags = new ArrayList<>();
        this.uploadId = createTransform.apply(s3 //
                .path(bucket, key) //
                .query("uploads") //
                .method(HttpMethod.POST)) //
                .responseAsXml() //
                .content("UploadId");
    }

    public static Builder s3(Client s3) {
        return new Builder(s3);
    }

    public static final class Builder {

        private final Client s3;
        private String bucket;
        public String key;
        public ExecutorService executor;
        public long timeoutMs;
        public Function<? super Request, ? extends Request> transform;

        public Builder(Client s3) {
            this.s3 = s3;
        }

        public Builder2 bucket(String bucket) {
            Preconditions.checkNotNull(bucket, "bucket cannot be null");
            this.bucket = bucket;
            return new Builder2(this);
        }
    }

    public static final class Builder2 {

        private final Builder b;

        public Builder2(Builder b) {
            this.b = b;
        }

        public Builder3 key(String key) {
            Preconditions.checkNotNull(key, "key cannot be null");
            b.key = key;
            return new Builder3(b);
        }
    }

    public static final class Builder3 {

        private final Builder b;

        public Builder3(Builder b) {
            this.b = b;
        }

        public Builder3 executor(ExecutorService executor) {
            b.executor = executor;
            return this;
        }

        public Builder3 timeout(long duration, TimeUnit unit) {
            Preconditions.checkArgument(duration > 0);
            b.timeoutMs = unit.toMillis(duration);
            return this;
        }

        public Builder3 transformCreateRequest(
                Function<? super Request, ? extends Request> transform) {
            b.transform = transform;
            return this;
        }

        public S3MultipartOutputStream build() {
            if (b.executor == null) {
                b.executor = Executors.newCachedThreadPool();
            }
            if (b.timeoutMs == 0) {
                b.timeoutMs = TimeUnit.HOURS.toMillis(1);
            }
            if (b.transform == null) {
                b.transform = x -> x;
            }
            return new S3MultipartOutputStream(b.s3, b.bucket, b.key, b.transform, b.executor,
                    b.timeoutMs);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        bytes.write(b, off, len);
        if (bytes.size() > THRESHOLD) {
            int part = nextPart;
            nextPart++;
            byte[] body = bytes.toByteArray();
            bytes.reset();
            executor.submit(() -> {
                // TODO set retry headers
                String etag = s3 //
                        .path(bucket, key) //
                        .method(HttpMethod.PUT) //
                        .query("partNumber", "1") //
                        .query("uploadId", uploadId) //
                        .requestBody(body) //
                        .response() //
                        .headers() //
                        .get("ETag") //
                        .get(0);
                setEtag(part, etag);
            });
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                Xml xml = Xml //
                        .create("CompleteMultipartUpload") //
                        .attribute("xmlns", "http:s3.amazonaws.com/doc/2006-03-01/");
                for (int i = 0; i < etags.size(); i++) {
                    xml = xml //
                            .element("Part") //
                            .element("ETag").content(etags.get(i)) //
                            .up() //
                            .element("PartNumber").content("" + i + 1) //
                            .up().up();
                }
                s3.path(bucket, key) //
                        .method(HttpMethod.POST) //
                        .query("uploadId", uploadId) //
                        .header("Content-Type", "application/xml") //
                        .unsignedPayload() //
                        .requestBody(xml.toString()) //
                        .execute();
            } else {
                throw new IOException("exceeded timeout of " + timeoutMs + "ms");
            }
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private synchronized void setEtag(int part, String etag) {
        // part is one-based

        // ensure etags is big enough
        for (int i = 0; i < etags.size() - part; i++) {
            etags.add(null);
        }
        etags.set(part - 1, etag);
    }

    @Override
    public void write(int b) throws IOException {
        singleByte[0] = (byte) b;
        write(singleByte, 0, 1);
    }
}
