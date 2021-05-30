package com.github.davidmoten.aws.lw.client;

import com.github.davidmoten.aws.lw.client.internal.util.HttpUtils;

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

    public Requester.Builder pathAndQuery(String pathAndQuery) {
        return Requester.clientAndUrl(this, "https://" + serviceName + "." + regionName
                + ".amazonaws.com/" + removeLeadingSlash(pathAndQuery));
    }

    public QueryBuilder path(String path) {
        return new QueryBuilder(this, path);
    }

    public QueryBuilder query(String name, String value) {
        return new QueryBuilder(this, "").query(name, value);
    }

    public static final class QueryBuilder {

        private final Client client;
        private String path;

        public QueryBuilder(Client client, String path) {
            this.client = client;
            this.path = path;
        }

        public QueryBuilder query(String name, String value) {
            if (!path.contains("?")) {
                path += "?";
            }
            if (!path.endsWith("?")) {
                path += "&";
            }
            path += HttpUtils.urlEncode(name, false) + "=" + HttpUtils.urlEncode(value, false);
            return this;
        }

        public com.github.davidmoten.aws.lw.client.Requester.Builder2 method(HttpMethod method) {
            return client.pathAndQuery(path).method(method);
        }
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
