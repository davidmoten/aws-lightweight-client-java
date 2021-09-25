package com.github.davidmoten.aws.lw.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import com.github.davidmoten.aws.lw.client.internal.util.Preconditions;
import com.github.davidmoten.aws.lw.client.xml.builder.Xml;

public final class MultipartOutputStream extends OutputStream {

    private final Client s3;
    private final String bucket;
    private final String key;
    private final String uploadId;
    private final ExecutorService executor;
    private final ByteArrayOutputStream bytes;
    private final List<String> etags;
    private final byte[] singleByte = new byte[1]; // for reuse in write(int) method
    private final long partTimeoutMs;
    private final int maxAttempts;
    private final long retryIntervalMs;
    private final int partSize;
    private final List<Future<?>> futures = new CopyOnWriteArrayList<>();
    private int nextPart = 1;

    MultipartOutputStream(Client s3, String bucket, String key,
            Function<? super Request, ? extends Request> createTransform, ExecutorService executor,
            long partTimeoutMs, int maxAttempts, long retryIntervalMs, int partSize) {
        Preconditions.checkNotNull(s3);
        Preconditions.checkNotNull(bucket);
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(createTransform);
        Preconditions.checkNotNull(executor);
        Preconditions.checkArgument(partTimeoutMs > 0);
        Preconditions.checkArgument(maxAttempts >= 1);
        Preconditions.checkArgument(retryIntervalMs >= 0);
        Preconditions.checkArgument(partSize >= 5 * 1024 * 1024);
        this.s3 = s3;
        this.bucket = bucket;
        this.key = key;
        this.executor = executor;
        this.partTimeoutMs = partTimeoutMs;
        this.maxAttempts = maxAttempts;
        this.retryIntervalMs = retryIntervalMs;
        this.partSize = partSize;
        this.bytes = new ByteArrayOutputStream();
        this.etags = new ArrayList<>();
        this.uploadId = createTransform.apply(s3 //
                .path(bucket, key) //
                .query("uploads") //
                .method(HttpMethod.POST)) //
                .responseAsXml() //
                .content("UploadId");
    }

    public void abort() {
        executor.shutdownNow();
        s3 //
                .path(bucket, key) //
                .query("uploadId", uploadId) //
                .method(HttpMethod.DELETE) //
                .execute();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int remaining = partSize - bytes.size();
            int n = Math.min(remaining, len);
            bytes.write(b, off, n);
            off += n;
            len -= n;
            if (bytes.size() == partSize) {
                submitPart();
            }
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
                    etag = s3 //
                            .path(bucket, key) //
                            .method(HttpMethod.PUT) //
                            .query("partNumber", "" + part) //
                            .query("uploadId", uploadId) //
                            .requestBody(body) //
                            .readTimeout(partTimeoutMs, TimeUnit.MILLISECONDS) //
                            .response() //
                            .headers() //
                            .get("ETag") //
                            .get(0) //
                            .replace("\"", "");
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
                abort();
                throw new RuntimeException(e);
            }
        }
        Xml xml = Xml //
                .create("CompleteMultipartUpload") //
                .attribute("xmlns", "http:s3.amazonaws.com/doc/2006-03-01/");
        for (int i = 0; i < etags.size(); i++) {
            xml = xml //
                    .element("Part") //
                    .element("ETag").content(etags.get(i)) //
                    .up() //
                    .element("PartNumber").content(String.valueOf(i + 1)) //
                    .up().up();
        }
        s3.path(bucket, key) //
                .method(HttpMethod.POST) //
                .query("uploadId", uploadId) //
                .header("Content-Type", "application/xml") //
                .unsignedPayload() //
                .requestBody(xml.toString()) //
                .execute();
    }

    private synchronized void setEtag(int part, String etag) {
        // part is one-based

        // ensure etags is big enough
        int extra = part - etags.size();
        for (int i = 0; i < extra; i++) {
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
