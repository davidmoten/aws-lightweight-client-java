package com.github.davidmoten.aws.lw.client;

import nanoxml.XMLElement;

public class ClientMain {

    public static void main(String[] args) {
        String regionName = "ap-southeast-2";
        String accessKey = System.getProperty("accessKey");
        String secretKey = System.getProperty("secretKey");

        Credentials credentials = Credentials.of(accessKey, secretKey);
        {
            Client sqs = Client //
                    .service("sqs") //
                    .regionName(regionName) //
                    .credentials(credentials);
            String path = "?Action=GetQueueUrl&QueueName=amsa-xml-in";
            XMLElement xml = sqs.path(path).method(HttpMethod.GET).executeXml();
            System.out.println(xml.content("GetQueueUrlResult", "QueueUrl"));
        }
        {
            Client s3 = Client.service("s3") //
                    .regionName(regionName) //
                    .credentials(credentials);
            String bucketName = "amsa-xml-in";
            s3.path(bucketName + "/ExampleObject.txt").method(HttpMethod.GET)
                    .executeUtf8(x -> System.out.println(x.length() + " chars read"));

            // put data int bucket
            s3.path(bucketName + "/ExampleObject.txt").method(HttpMethod.PUT)
                    .requestBody("hi there").execute();
            System.out.println("put object completed");
        }

    }
}
