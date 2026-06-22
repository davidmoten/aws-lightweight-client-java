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

public class STSCredentialsProviderTest {

    @Test
    public void testIsUtilityClass() {
        Asserts.assertIsUtilityClass(STSCredentialsProvider.class);
    }

    @Test
    public void testProviderReturnsEmptyWhenNoTokenFile() {
        Map<String, String> map = Maps //
                .put("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/test-role") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = STSCredentialsProvider.credentials(env,
                new QueueHttpClient(), Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testProviderReturnsEmptyWhenNoRoleArn() {
        Map<String, String> map = Maps //
                .put("AWS_WEB_IDENTITY_TOKEN_FILE", "src/test/resources/test.txt") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = STSCredentialsProvider.credentials(env,
                new QueueHttpClient(), Clock.DEFAULT);
        assertFalse(result.isPresent());
    }

    @Test
    public void testBuildRequestBodyContainsExpectedParameters() {
        byte[] body = STSCredentialsProvider.buildRequestBody(
                "arn:aws:iam::123456789012:role/test-role",
                "test-session", "test-token");
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("Action=AssumeRoleWithWebIdentity"));
        assertTrue(bodyStr.contains("Version=2011-06-15"));
        assertTrue(bodyStr.contains("RoleArn="));
        assertTrue(bodyStr.contains("arn%3Aaws%3Aiam%3A%3A123456789012%3Arole%2Ftest-role"));
        assertTrue(bodyStr.contains("RoleSessionName=test-session"));
        assertTrue(bodyStr.contains("WebIdentityToken=test-token"));
        assertTrue(bodyStr.contains("DurationSeconds=3600"));
    }

    @Test
    public void testParseStsResponse() {
        String xml = "<AssumeRoleWithWebIdentityResponse xmlns=\"https://sts.amazonaws.com/doc/2011-06-15/\">\n"
                + "  <AssumeRoleWithWebIdentityResult>\n"
                + "    <Credentials>\n"
                + "      <AccessKeyId>ASIA_TEST_KEY</AccessKeyId>\n"
                + "      <SecretAccessKey>test-secret-key</SecretAccessKey>\n"
                + "      <SessionToken>test-session-token</SessionToken>\n"
                + "      <Expiration>2014-10-24T23:00:23Z</Expiration>\n"
                + "    </Credentials>\n"
                + "  </AssumeRoleWithWebIdentityResult>\n"
                + "</AssumeRoleWithWebIdentityResponse>";
        Credentials creds = STSCredentialsProvider.parseStsResponse(xml);
        assertEquals("ASIA_TEST_KEY", creds.accessKey());
        assertEquals("test-secret-key", creds.secretKey());
        assertEquals("test-session-token", creds.sessionToken().get());
        assertEquals(Instant.parse("2014-10-24T23:00:23Z"), creds.expiration().get());
    }

    @Test(expected = RuntimeException.class)
    public void testParseStsResponseMalformedXml() {
        STSCredentialsProvider.parseStsResponse("not xml");
    }

    @Test(expected = RuntimeException.class)
    public void testParseStsResponseMissingCredentialsElement() {
        STSCredentialsProvider.parseStsResponse("<root><other/></root>");
    }

    @Test
    public void testStsEndpointGlobalByDefault() {
        Map<String, String> map = new HashMap<>();
        Environment env = map::get;
        String endpoint = STSCredentialsProvider.stsEndpoint(env);
        assertEquals("https://sts.amazonaws.com/", endpoint);
    }

    @Test
    public void testStsEndpointRegional() {
        Map<String, String> map = Maps //
                .put("AWS_STS_REGIONAL_ENDPOINTS", "regional") //
                .put("AWS_REGION", "us-west-2") //
                .buildImmutable();
        Environment env = map::get;
        String endpoint = STSCredentialsProvider.stsEndpoint(env);
        assertEquals("https://sts.us-west-2.amazonaws.com/", endpoint);
    }

    @Test
    public void testStsEndpointRegionalWithDefaultRegion() {
        Map<String, String> map = Maps //
                .put("AWS_STS_REGIONAL_ENDPOINTS", "regional") //
                .put("AWS_DEFAULT_REGION", "eu-west-1") //
                .buildImmutable();
        Environment env = map::get;
        String endpoint = STSCredentialsProvider.stsEndpoint(env);
        assertEquals("https://sts.eu-west-1.amazonaws.com/", endpoint);
    }

    @Test
    public void testStsEndpointOverride() {
        Map<String, String> map = Maps //
                .put("AWS_STS_ENDPOINT_URL", "http://localhost:12345/") //
                .buildImmutable();
        Environment env = map::get;
        String endpoint = STSCredentialsProvider.stsEndpoint(env);
        assertEquals("http://localhost:12345/", endpoint);
    }

    @Test
    public void testSuccessfulStsCallReturnsAutoRefreshingCredentials() {
        long baseTime = 1700000000000L;
        SettableClock clock = new SettableClock(baseTime);
        String expiration = Instant.ofEpochMilli(baseTime + 3600_000).toString();
        String xml = stsResponseXml("KEY1", "secret1", "token1", expiration);

        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, xml);

        Map<String, String> map = Maps //
                .put("AWS_STS_ENDPOINT_URL", "http://localhost/test") //
                .put("AWS_WEB_IDENTITY_TOKEN_FILE", "src/test/resources/test.txt") //
                .put("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/test-role") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = STSCredentialsProvider.credentials(env,
                httpClient, clock);
        assertTrue(result.isPresent());
        Credentials creds = result.get();
        assertEquals("KEY1", creds.accessKey());
        assertEquals("secret1", creds.secretKey());
        assertEquals("token1", creds.sessionToken().get());
        assertEquals(Instant.ofEpochMilli(baseTime + 3600_000), creds.expiration().get());
    }

    @Test
    public void testAutoRefreshTriggersWhenNearExpiry() {
        long baseTime = 1700000000000L;
        SettableClock clock = new SettableClock(baseTime);
        String expiration1 = Instant.ofEpochMilli(baseTime + 360_000).toString(); // 6 min expiry
        String expiration2 = Instant.ofEpochMilli(baseTime + 720_000).toString(); // 12 min expiry

        String xml1 = stsResponseXml("KEY1", "secret1", "token1", expiration1);
        String xml2 = stsResponseXml("KEY2", "secret2", "token2", expiration2);

        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, xml1);
        httpClient.enqueue(200, xml2);

        Map<String, String> map = Maps //
                .put("AWS_STS_ENDPOINT_URL", "http://localhost/test") //
                .put("AWS_WEB_IDENTITY_TOKEN_FILE", "src/test/resources/test.txt") //
                .put("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/test-role") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = STSCredentialsProvider.credentials(env,
                httpClient, clock);
        Credentials creds = result.get();
        assertEquals("KEY1", creds.accessKey());

        clock.advance(200_000); // advance past threshold (exp-300k = base+60k, clock = base+200k)
        assertEquals("KEY2", creds.accessKey());
    }

    @Test
    public void testAutoRefreshDoesNotTriggerFarFromExpiry() {
        long baseTime = 1700000000000L;
        SettableClock clock = new SettableClock(baseTime);
        String expiration = Instant.ofEpochMilli(baseTime + 3600_000).toString(); // 1 hour expiry

        String xml = stsResponseXml("KEY1", "secret1", "token1", expiration);

        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, xml);

        Map<String, String> map = Maps //
                .put("AWS_STS_ENDPOINT_URL", "http://localhost/test") //
                .put("AWS_WEB_IDENTITY_TOKEN_FILE", "src/test/resources/test.txt") //
                .put("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/test-role") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = STSCredentialsProvider.credentials(env,
                httpClient, clock);
        Credentials creds = result.get();
        assertEquals("KEY1", creds.accessKey());

        clock.advance(120_000); // still 58 min from expiry
        assertEquals("KEY1", creds.accessKey()); // no refresh
    }

    @Test
    public void testAutoRefreshThrowsWhenExpiredAndStsFails() {
        long baseTime = 1700000000000L;
        SettableClock clock = new SettableClock(baseTime);
        String expiration = Instant.ofEpochMilli(baseTime + 360_000).toString(); // 6 min expiry

        String xml = stsResponseXml("KEY1", "secret1", "token1", expiration);

        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, xml);
        httpClient.enqueue(500, "Internal Server Error");

        Map<String, String> map = Maps //
                .put("AWS_STS_ENDPOINT_URL", "http://localhost/test") //
                .put("AWS_WEB_IDENTITY_TOKEN_FILE", "src/test/resources/test.txt") //
                .put("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/test-role") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = STSCredentialsProvider.credentials(env,
                httpClient, clock);
        Credentials creds = result.get();
        assertEquals("KEY1", creds.accessKey());

        clock.advance(400_000); // past 6 min expiry
        try {
            creds.accessKey();
            fail("Expected exception was not thrown");
        } catch (RuntimeException e) {
            // expected - creds expired and refresh failed
        }
    }

    @Test
    public void testAutoRefreshKeepsCurrentWhenStsFailsBeforeExpiry() {
        long baseTime = 1700000000000L;
        SettableClock clock = new SettableClock(baseTime);
        String expiration = Instant.ofEpochMilli(baseTime + 360_000).toString(); // 6 min expiry

        String xml = stsResponseXml("KEY1", "secret1", "token1", expiration);

        QueueHttpClient httpClient = new QueueHttpClient();
        httpClient.enqueue(200, xml);
        httpClient.enqueue(500, "Internal Server Error");

        Map<String, String> map = Maps //
                .put("AWS_STS_ENDPOINT_URL", "http://localhost/test") //
                .put("AWS_WEB_IDENTITY_TOKEN_FILE", "src/test/resources/test.txt") //
                .put("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/test-role") //
                .buildImmutable();
        Environment env = map::get;
        Optional<Credentials> result = STSCredentialsProvider.credentials(env,
                httpClient, clock);
        Credentials creds = result.get();
        assertEquals("KEY1", creds.accessKey());

        clock.advance(200_000); // within 5 min window but before expiry
        assertEquals("KEY1", creds.accessKey()); // refresh fails, keep current
    }

    @Test
    public void testAutoRefreshStaticCredentialsNeverRefresh() {
        long baseTime = 1700000000000L;
        SettableClock clock = new SettableClock(baseTime);
        Credentials staticCreds = Credentials.of("KEY1", "secret1");
        ExpiringCredentialsImpl auto = new ExpiringCredentialsImpl(staticCreds,
                () -> {
                    throw new RuntimeException("should not be called");
                }, clock);
        assertEquals("KEY1", auto.accessKey());
        clock.advance(3600_000);
        assertEquals("KEY1", auto.accessKey()); // no refresh needed, no exception
    }

    @Test
    public void testSystemPropertiesPreferredOverEnvVarsForWebIdentity() {
        System.setProperty("aws.webIdentityTokenFile", "src/test/resources/test.txt");
        System.setProperty("aws.roleArn", "arn:aws:iam::123456789012:role/sysprop-role");
        try {
            long baseTime = 1700000000000L;
            SettableClock clock = new SettableClock(baseTime);
            String expiration = Instant.ofEpochMilli(baseTime + 3600_000).toString();
            String xml = stsResponseXml("SYSKEY", "syssecret", "systoken", expiration);

            QueueHttpClient httpClient = new QueueHttpClient();
            httpClient.enqueue(200, xml);

            Map<String, String> map = Maps //
                    .put("AWS_STS_ENDPOINT_URL", "http://localhost/test") //
                    .put("AWS_WEB_IDENTITY_TOKEN_FILE", "nonexistent-file") //
                    .put("AWS_ROLE_ARN", "arn:aws:iam::999999999999:role/ignored-role") //
                    .buildImmutable();
            Environment env = map::get;
            Optional<Credentials> result = STSCredentialsProvider.credentials(env,
                    httpClient, clock);
            assertTrue(result.isPresent());
            assertEquals("SYSKEY", result.get().accessKey());
        } finally {
            System.clearProperty("aws.webIdentityTokenFile");
            System.clearProperty("aws.roleArn");
        }
    }

    private static String stsResponseXml(String accessKeyId, String secretAccessKey,
            String sessionToken, String expiration) {
        return "<AssumeRoleWithWebIdentityResponse xmlns=\"https://sts.amazonaws.com/doc/2011-06-15/\">\n"
                + "  <AssumeRoleWithWebIdentityResult>\n"
                + "    <Credentials>\n"
                + "      <AccessKeyId>" + accessKeyId + "</AccessKeyId>\n"
                + "      <SecretAccessKey>" + secretAccessKey + "</SecretAccessKey>\n"
                + "      <SessionToken>" + sessionToken + "</SessionToken>\n"
                + "      <Expiration>" + expiration + "</Expiration>\n"
                + "    </Credentials>\n"
                + "  </AssumeRoleWithWebIdentityResult>\n"
                + "</AssumeRoleWithWebIdentityResponse>";
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
        private final Queue<ResponseEntry> queue = new LinkedList<>();

        void enqueue(int statusCode, String body) {
            queue.add(new ResponseEntry(statusCode, body));
        }

        @Override
        public ResponseInputStream request(URL endpointUrl, String httpMethod,
                Map<String, String> headers, byte[] requestBody, int connectTimeoutMs,
                int readTimeoutMs) throws IOException {
            ResponseEntry entry = queue.poll();
            if (entry == null) {
                throw new IOException("No more queued responses");
            }
            return new ResponseInputStream(() -> {
            }, entry.statusCode, Collections.emptyMap(),
                    entry.body != null
                            ? new ByteArrayInputStream(entry.body.getBytes(StandardCharsets.UTF_8))
                            : Util.emptyInputStream());
        }

        private static final class ResponseEntry {
            final int statusCode;
            final String body;

            ResponseEntry(int statusCode, String body) {
                this.statusCode = statusCode;
                this.body = body;
            }
        }
    }
}
