package com.github.davidmoten.aws.lw.client;

import nanoxml.XMLElement;

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

    public String serviceName() {
        return serviceName;
    }

    public String regionName() {
        return regionName;
    }

    public String accessKey() {
        return accessKey;
    }

    public String secretKey() {
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

    public static void main(String[] args) {
        String regionName = "ap-southeast-2";
        String accessKey = System.getProperty("accessKey");
        String secretKey = System.getProperty("secretKey");

        Credentials credentials = Credentials.of(accessKey, secretKey);
        {
            Client sqs = Client.service("sqs") //
                    .regionName(regionName) //
                    .credentials(credentials);
            String url = "https://sqs." + regionName
                    + ".amazonaws.com/?Action=GetQueueUrl&QueueName=amsa-xml-in&Version=2012-11-05";
            XMLElement xml = sqs.url(url).method(HttpMethod.GET).executeXml();
            System.out.println(xml.firstChild().firstChild().getContent());
            System.exit(0);
        }
        {
            Client s3 = Client.service("s3") //
                    .regionName(regionName) //
                    .credentials(credentials);
            String bucketName = "amsa-xml-in";
            String url = "https://" + bucketName + ".s3.amazonaws.com/driveItem.txt";
            s3.url(url).method(HttpMethod.GET).executeUtf8(System.out::println);
        }

    }

}
