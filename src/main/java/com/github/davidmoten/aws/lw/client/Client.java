package com.github.davidmoten.aws.lw.client;

public final class Client {

    private final String serviceName;
    private final String regionName;
    private final Credentials credentials;

    private Client(String serviceName, String regionName, Credentials credentials) {
        this.serviceName = serviceName;
        this.regionName = regionName;
        this.credentials = credentials;
    }

    String serviceName() {
        return serviceName;
    }

    String regionName() {
        return regionName;
    }

    public Credentials credentials() {
        return credentials;
    }

    public Requester.Builder url(String url) {
        return Requester.clientAndUrl(this, url);
    }

    /**
     * Specify the path (can include query).
     * 
     * @param path can include query parts as well (stuff after ?)
     * @return builder
     */
    public Requester.Builder path(String path) {
        return url("https://" + serviceName + "." + regionName + ".amazonaws.com/"
                + removeLeadingSlash(path));
    }

    public Requester.Builder query(String name, String value) {
        return path("").query(name, value);
    }

    private static String removeLeadingSlash(String s) {
        if (s.startsWith("/")) {
            return s.substring(1);
        } else {
            return s;
        }
    }

    public static Builder service(String serviceName) {
        return new Builder(serviceName);
    }

    public static Builder s3() {
        return new Builder("s3");
    }

    public static Builder sqs() {
        return new Builder("sqs");
    }

    public static final class Builder {

        private final String serviceName;
        private String regionName;
        private String accessKey;
        public Credentials credentials;

        private Builder(String serviceName) {
            this.serviceName = serviceName;
        }

        public Client defaultClient() {
            return regionFromEnvironment().credentials(Credentials.fromEnvironment());
        }

        public Client from(Client client) {
            return regionName(client.regionName()).credentials(client.credentials());
        }

        public Builder2 regionFromEnvironment() {
            return regionName(System.getenv("AWS_REGION"));
        }

        public Builder2 regionName(String regionName) {
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
            b.accessKey = accessKey;
            return new Builder3(b);
        }

        public Client credentials(Credentials credentials) {
            return new Client(b.serviceName, b.regionName, credentials);
        }
    }

    public static final class Builder3 {
        private final Builder b;

        private Builder3(Builder b) {
            this.b = b;
        }

        public Client secretKey(String secretKey) {
            Credentials c = Credentials.of(b.accessKey, secretKey);
            return new Client(b.serviceName, b.regionName, c);
        }
    }
}
