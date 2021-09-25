package com.github.davidmoten.aws.lw.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import com.github.davidmoten.aws.lw.client.internal.util.Preconditions;
import com.github.davidmoten.aws.lw.client.xml.builder.Xml;

public final class MultipartOutputStream extends OutputStream {

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
    private final int maxAttempts;
    private final long retryIntervalMs;
    private final List<Future<?>> futures = new CopyOnWriteArrayList<>();
    private int nextPart = 1;

    private MultipartOutputStream(Client s3, String bucket, String key,
            Function<? super Request, ? extends Request> createTransform, ExecutorService executor,
            long timeoutMs, int maxAttempts, long retryIntervalMs) {
        Preconditions.checkNotNull(s3);
        Preconditions.checkNotNull(bucket);
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(createTransform);
        Preconditions.checkNotNull(executor);
        Preconditions.checkArgument(timeoutMs > 0);
        Preconditions.checkArgument(maxAttempts >= 1);
        Preconditions.checkArgument(retryIntervalMs >= 0);
        this.s3 = s3;
        this.bucket = bucket;
        this.key = key;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
        this.maxAttempts = maxAttempts;
        this.retryIntervalMs = retryIntervalMs;
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
        public long timeoutMs = TimeUnit.HOURS.toMillis(1);
        public Function<? super Request, ? extends Request> transform = x -> x;
        public int maxAttempts = 3;
        public int retryIntervalMs = 30000;

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

        public MultipartOutputStream build() {
            if (b.executor == null) {
                b.executor = Executors.newCachedThreadPool();
            }
            return new MultipartOutputStream(b.s3, b.bucket, b.key, b.transform, b.executor,
                    b.timeoutMs, b.maxAttempts, b.retryIntervalMs);
        }
    }

    public void abort() {
        s3 //
                .path(bucket, key) //
                .query("uploadId", uploadId) //
                .method(HttpMethod.DELETE) //
                .execute();
        executor.shutdownNow();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        bytes.write(b, off, len);
        if (bytes.size() > THRESHOLD) {
            submitPart();
        }
    }

    private void submitPart() {
        int part = nextPart;
        nextPart++;
        byte[] body = bytes.toByteArray();
        bytes.reset();
        Future<?> future = executor.submit(() -> {
            int attempt = 1;
            String etag;
            while (true) {
                try {
                    System.out.println("starting upload of part " + part);
                    etag = s3 //
                            .path(bucket, key) //
                            .method(HttpMethod.PUT) //
                            .query("partNumber", "" + part) //
                            .query("uploadId", uploadId) //
                            .requestBody(body) //
                            .readTimeout(1, TimeUnit.HOURS) //
                            .response() //
                            .headers() //
                            .get("ETag") //
                            .get(0);
                    System.out.println("finished upload of part " + part);
                    break;
                } catch (Throwable e) {
                    e.printStackTrace();
                    // Note could do using ScheduledExecutorService rather than blocking the
                    // thread here
                    try {
                        Thread.sleep(retryIntervalMs);
                    } catch (InterruptedException e1) {
                        // ignore
                    }
                    attempt++;
                    if (attempt > maxAttempts) {
                        throw new RuntimeException(
                                "exceeded max attempts " + maxAttempts + " on part " + part);
                    }
                }
            }
            setEtag(part, etag);
        });
        futures.add(future);
    }

    @Override
    public void close() throws IOException {
        // submit whatever's left
        if (bytes.size() > 0) {
            submitPart();
        }
        for (Future<?> future : futures) {
            try {
                future.get(1, TimeUnit.HOURS);
            } catch (ExecutionException | TimeoutException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("finished parts upload, completing");
        System.out.println(etags);
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
        System.out.println("completed");
    }

    private synchronized void setEtag(int part, String etag) {
        // part is one-based

        // ensure etags is big enough
        for (int i = 0; i < part - etags.size(); i++) {
            etags.add("not set");
        }
        etags.set(part - 1, etag);
    }

    @Override
    public void write(int b) throws IOException {
        singleByte[0] = (byte) b;
        write(singleByte, 0, 1);
    }
}
