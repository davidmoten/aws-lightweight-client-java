package com.github.davidmoten.aws.lw.client.internal;

import com.github.davidmoten.aws.lw.client.Credentials;
import com.github.davidmoten.aws.lw.client.HttpClient;
import com.github.davidmoten.aws.lw.client.ResponseInputStream;
import com.github.davidmoten.aws.lw.client.internal.util.Util;
import com.github.davidmoten.guavamini.Maps;
import com.github.davidmoten.junit.Asserts;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class InstanceProfileCredentialsProviderTest {

    @Test
    public void testIsUtilityClass() {
        Asserts.assertIsUtilityClass(InstanceProfileCredentialsProvider.class);
    }

    @Test
    public void testReturnsEmptyWhenDisabledViaEnvVar() {
        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_DISABLED", "true") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                new QueueHttpClient(), Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testReturnsEmptyWhenDisabledViaSystemProperty() {
        System.setProperty("aws.disableEc2Metadata", "true");
        try {
            Environment env = new HashMap<String, String>()::get;
            Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                    new QueueHttpClient(), Clock.DEFAULT);
            assertFalse(result.isPresent());
        } finally {
            System.clearProperty("aws.disableEc2Metadata");
        }
    }

    @Test
    public void testDefaultEndpoint() {
        Environment env = new HashMap<String, String>()::get;
        assertEquals("http://169.254.169.254",
                InstanceProfileCredentialsProvider.endpoint(env));
    }

    @Test
    public void testEndpointOverrideViaEnvVar() {
        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://10.0.0.1") //
                .buildImmutable();
        Environment env = map::get;
        assertEquals("http://10.0.0.1",
                InstanceProfileCredentialsProvider.endpoint(env));
    }

    @Test
    public void testEndpointOverrideViaSystemProperty() {
        System.setProperty("aws.ec2MetadataServiceEndpoint", "http://10.0.0.2");
        try {
            Environment env = new HashMap<String, String>()::get;
            assertEquals("http://10.0.0.2",
                    InstanceProfileCredentialsProvider.endpoint(env));
        } finally {
            System.clearProperty("aws.ec2MetadataServiceEndpoint");
        }
    }

    @Test
    public void testSuccessfulImdsFlowReturnsCredentials() {
        long baseTime = 1700000000000L;
        SettableClock clock = new SettableClock(baseTime);
        String expiration = Instant.ofEpochMilli(baseTime + 3600_000).toString();

        QueueHttpClient httpClient = new QueueHttpClient();
        // IMDSv2 token
        httpClient.enqueue(200, "test-token-abc123\n");
        // role name
        httpClient.enqueue(200, "my-test-role\n");
        // credentials
        httpClient.enqueue(200, imdsResponseJson("KEY1", "secret1", "token1", expiration));

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, clock);
        assertTrue(result.isPresent());
        Credentials creds = result.get();
        assertEquals("KEY1", creds.accessKey());
        assertEquals("secret1", creds.secretKey());
        assertEquals("token1", creds.sessionToken().get());
        assertEquals(Instant.ofEpochMilli(baseTime + 3600_000), creds.expiration().get());
    }

    @Test
    public void testReturnsEmptyWhenTokenEndpointUnreachable() {
        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueueError(new IOException("Connection refused"));

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testReturnsEmptyWhenTokenEndpointReturnsError() {
        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(500, "Internal Server Error");

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testReturnsEmptyWhenRoleNameEndpointFails() {
        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, "test-token");
        httpClient.enqueue(500, "Not Found");

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testReturnsEmptyWhenCredentialsEndpointFails() {
        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, "test-token");
        httpClient.enqueue(200, "my-role");
        httpClient.enqueue(500, "Not Found");

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testAutoRefreshTriggersWhenNearExpiry() {
        long baseTime = 1700000000000L;
        SettableClock clock = new SettableClock(baseTime);
        String expiration1 = Instant.ofEpochMilli(baseTime + 360_000).toString(); // 6 min
        String expiration2 = Instant.ofEpochMilli(baseTime + 720_000).toString(); // 12 min

        QueueHttpClient httpClient = new QueueHttpClient();
        // First credential fetch: token + role + creds
        httpClient.enqueue(200, "token1");
        httpClient.enqueue(200, "my-role");
        httpClient.enqueue(200, imdsResponseJson("KEY1", "secret1", "token1", expiration1));
        // Second credential fetch (refresh): token + role + creds
        httpClient.enqueue(200, "token2");
        httpClient.enqueue(200, "my-role");
        httpClient.enqueue(200, imdsResponseJson("KEY2", "secret2", "token2", expiration2));

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, clock);
        Credentials creds = result.get();
        assertEquals("KEY1", creds.accessKey());

        clock.advance(200_000); // past threshold (exp-300k = base+60k)
        assertEquals("KEY2", creds.accessKey());
    }

    @Test
    public void testAutoRefreshDoesNotTriggerFarFromExpiry() {
        long baseTime = 1700000000000L;
        SettableClock clock = new SettableClock(baseTime);
        String expiration = Instant.ofEpochMilli(baseTime + 3600_000).toString(); // 1 hour

        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, "token1");
        httpClient.enqueue(200, "my-role");
        httpClient.enqueue(200, imdsResponseJson("KEY1", "secret1", "token1", expiration));

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, clock);
        Credentials creds = result.get();
        assertEquals("KEY1", creds.accessKey());

        clock.advance(120_000); // still 58 min from expiry
        assertEquals("KEY1", creds.accessKey()); // no refresh
    }

    @Test
    public void testReturnsEmptyWhenRoleNameIsEmpty() {
        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, "test-token");
        httpClient.enqueue(200, ""); // empty role name

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testReturnsEmptyWhenCredentialsMissingAccessKeyId() {
        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, "test-token");
        httpClient.enqueue(200, "my-role");
        // JSON missing AccessKeyId
        httpClient.enqueue(200, "{\"SecretAccessKey\":\"sk\",\"Token\":\"t\",\"Expiration\":\"2025-01-01T00:00:00Z\"}");

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testReturnsEmptyWhenCredentialsMissingSecretAccessKey() {
        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, "test-token");
        httpClient.enqueue(200, "my-role");
        httpClient.enqueue(200, "{\"AccessKeyId\":\"ak\",\"Token\":\"t\",\"Expiration\":\"2025-01-01T00:00:00Z\"}");

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testReturnsEmptyWhenCredentialsMissingSessionToken() {
        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, "test-token");
        httpClient.enqueue(200, "my-role");
        httpClient.enqueue(200,
                "{\"AccessKeyId\":\"ak\",\"SecretAccessKey\":\"sk\",\"Expiration\":\"2025-01-01T00:00:00Z\"}");

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testReturnsEmptyWhenCredentialsMissingExpiration() {
        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, "test-token");
        httpClient.enqueue(200, "my-role");
        httpClient.enqueue(200, "{\"AccessKeyId\":\"ak\",\"SecretAccessKey\":\"sk\",\"Token\":\"t\"}");

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testReturnsEmptyWhenTokenFetchThrowsIOException() {
        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueueError(new IOException("Connection refused"));

        Map<String, String> map = Maps //
                .put("AWS_EC2_METADATA_SERVICE_ENDPOINT", "http://169.254.169.254") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = InstanceProfileCredentialsProvider.credentials(env,
                httpClient, Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    private static String imdsResponseJson(String accessKeyId, String secretAccessKey,
            String sessionToken, String expiration) {
        return "{\n"
                + "  \"Code\" : \"Success\",\n"
                + "  \"LastUpdated\" : \"2012-04-26T16:39:16Z\",\n"
                + "  \"Type\" : \"AWS-HMAC\",\n"
                + "  \"AccessKeyId\" : \"" + accessKeyId + "\",\n"
                + "  \"SecretAccessKey\" : \"" + secretAccessKey + "\",\n"
                + "  \"Token\" : \"" + sessionToken + "\",\n"
                + "  \"Expiration\" : \"" + expiration + "\"\n"
                + "}";
    }

    static final class SettableClock implements Clock {
        private final AtomicLong time;

        SettableClock(long millis) {
            this.time = new AtomicLong(millis);
        }

        @Override
        public long time() {
            return time.get();
        }

        void advance(long millis) {
            time.addAndGet(millis);
        }
    }

    private static final class QueueHttpClient implements HttpClient {
        private final Queue<HttpResponse> queue = new LinkedList<>();

        void enqueue(int statusCode, String body) {
            queue.add(new HttpResponse(statusCode, body, null));
        }

        void enqueueError(IOException error) {
            queue.add(new HttpResponse(0, null, error));
        }

        @Override
        public ResponseInputStream request(URL endpointUrl, String httpMethod,
                Map<String, String> headers, byte[] requestBody, int connectTimeoutMs,
                int readTimeoutMs) throws IOException {
            HttpResponse entry = queue.poll();
            if (entry == null) {
                throw new IOException("No more queued responses");
            }
            if (entry.error != null) {
                throw entry.error;
            }
            return new ResponseInputStream(() -> {
            }, entry.statusCode, Collections.emptyMap(),
                    entry.body != null
                            ? new ByteArrayInputStream(entry.body.getBytes(StandardCharsets.UTF_8))
                            : Util.emptyInputStream());
        }

        private static final class HttpResponse {
            final int statusCode;
            final String body;
            final IOException error;

            HttpResponse(int statusCode, String body, IOException error) {
                this.statusCode = statusCode;
                this.body = body;
                this.error = error;
            }
        }
    }
}
