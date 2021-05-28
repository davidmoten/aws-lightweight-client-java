package com.github.davidmoten.aws.lw.client;

public final class Client {

    private final String serviceName;
    private final String regionName;
    private final String accessKey;
    private final String secretKey;

    private Client(String serviceName, String regionName, String accessKey, String secretKey) {
        this.serviceName = serviceName;
        this.regionName = regionName;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    String serviceName() {
        return serviceName;
    }

    String regionName() {
        return regionName;
    }

    String accessKey() {
        return accessKey;
    }

    String secretKey() {
        return secretKey;
    }

    public Requester.Builder url(String url) {
        return Requester.clientAndUrl(this, url);
    }

    public static Builder service(String serviceName) {
        return new Builder(serviceName);
    }

    public static final class Builder {

        private final String serviceName;
        private String regionName;
        private String accessKey;
        private String secretKey;

        private Builder(String serviceName) {
            this.serviceName = serviceName;
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
            return accessKey(credentials.accessKey()).secretKey(credentials.secretKey());
        }
    }

    public static final class Builder3 {
        private final Builder b;

        private Builder3(Builder b) {
            this.b = b;
        }

        public Client secretKey(String secretKey) {
            b.secretKey = secretKey;
            return new Client(b.serviceName, b.regionName, b.accessKey, b.secretKey);
        }
    }
}
