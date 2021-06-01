package com.github.davidmoten.aws.lw.client;

import com.github.davidmoten.aws.lw.client.internal.HttpClientDefault;
import com.github.davidmoten.xml.Preconditions;

public final class Client {

    private final String serviceName;
    private final String regionName;
    private final Credentials credentials;
    private final HttpClient httpClient;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private Client(String serviceName, String regionName, Credentials credentials, HttpClient httpClient,
            int connectTimeoutMs, int readTimeoutMs) {
        this.serviceName = serviceName;
        this.regionName = regionName;
        this.credentials = credentials;
        this.httpClient = httpClient;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public static Builder service(String serviceName) {
        Preconditions.checkNotNull(serviceName);
        return new Builder(serviceName);
    }

    ///////////////////////////////////////////////////
    //
    // Convenience methods for a few common services
    // Use service(serviceName) method for the rest
    //
    ///////////////////////////////////////////////////

    public static Builder s3() {
        return new Builder("s3");
    }

    public static Builder sqs() {
        return new Builder("sqs");
    }

    public static Builder iam() {
        return new Builder("iam");
    }

    public static Builder ec2() {
        return new Builder("ec2");
    }

    public static Builder sns() {
        return new Builder("sns");
    }

    public static Builder lambda() {
        return new Builder("lambda");
    }

    ///////////////////////////////////////////////////

    String serviceName() {
        return serviceName;
    }

    String regionName() {
        return regionName;
    }

    Credentials credentials() {
        return credentials;
    }

    HttpClient httpClient() {
        return httpClient;
    }

    int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    int readTimeoutMs() {
        return readTimeoutMs;
    }

    public Request url(String url) {
        Preconditions.checkNotNull(url);
        return Request.clientAndUrl(this, url);
    }

    /**
     * Specify the path (can include query).
     * 
     * @param path can include query parts as well (stuff after ?)
     * @return builder
     */
    public Request path(String path) {
        Preconditions.checkNotNull(path);
        return url("https://" + serviceName + "." + regionName + ".amazonaws.com/" + removeLeadingSlash(path));
    }

    public Request query(String name, String value) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(value);
        return path("").query(name, value);
    }
    
    public Request attribute(String name, String value) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(value);
        return path("").attribute(name, value);
    }

    private static String removeLeadingSlash(String s) {
        Preconditions.checkNotNull(s);
        if (s.startsWith("/")) {
            return s.substring(1);
        } else {
            return s;
        }
    }

    public static final class Builder {

        private final String serviceName;
        private String regionName;
        private String accessKey;
        private Credentials credentials;
        private HttpClient httpClient = HttpClientDefault.INSTANCE;
        private int connectTimeoutMs = 30000;
        private int readTimeoutMs = 300000;

        private Builder(String serviceName) {
            this.serviceName = serviceName;
        }

        public Client defaultClient() {
            return regionFromEnvironment().credentials(Credentials.fromEnvironment()).build();
        }

        public Client from(Client client) {
            Preconditions.checkNotNull(client);
            return regionName(client.regionName()).credentials(client.credentials()).build();
        }

        public Builder2 regionFromEnvironment() {
            return regionName(System.getenv("AWS_REGION"));
        }

        public Builder2 regionName(String regionName) {
            Preconditions.checkNotNull(regionName);
            this.regionName = regionName;
            return new Builder2(this);
        }
    }

    public static final class Builder2 {
        private final Builder b;

        private Builder2(Builder b) {
            this.b = b;
        }

        public Builder3 accessKey(String accessKey) {
            Preconditions.checkNotNull(accessKey);
            b.accessKey = accessKey;
            return new Builder3(b);
        }

        public Builder4 credentials(Credentials credentials) {
            Preconditions.checkNotNull(credentials);
            b.credentials = credentials;
            return new Builder4(b);
        }
    }

    public static final class Builder3 {
        private final Builder b;

        private Builder3(Builder b) {
            this.b = b;
        }

        public Builder4 secretKey(String secretKey) {
            Preconditions.checkNotNull(secretKey);
            b.credentials = Credentials.of(b.accessKey, secretKey);
            return new Builder4(b);
        }
    }

    public static final class Builder4 {
        private final Builder b;

        private Builder4(Builder b) {
            this.b = b;
        }

        public Builder4 httpClient(HttpClient httpClient) {
            b.httpClient = httpClient;
            return this;
        }

        public Builder4 connectTimeoutMs(int connectTimeoutMs) {
            b.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder4 readTimeoutMs(int readTimeoutMs) {
            b.readTimeoutMs = readTimeoutMs;
            return this;
        }

        public Client build() {
            return new Client(b.serviceName, b.regionName, b.credentials, b.httpClient, b.connectTimeoutMs,
                    b.readTimeoutMs);
        }
    }
}
