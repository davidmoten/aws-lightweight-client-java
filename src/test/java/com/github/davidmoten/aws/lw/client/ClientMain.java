package com.github.davidmoten.aws.lw.client;

import nanoxml.XMLElement;

public class ClientMain {

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
            System.out.println(xml.content("GetQueueUrlResult", "QueueUrl"));
        }
        {
            Client s3 = Client.service("s3") //
                    .regionName(regionName) //
                    .credentials(credentials);
            String bucketName = "amsa-xml-in";
            String url = "https://" + bucketName + ".s3.amazonaws.com/driveItem.txt";
            s3.url(url).method(HttpMethod.GET)
                    .executeUtf8(x -> System.out.println(x.length() + " chars read"));

            //
            String url2 = "https://s3." + regionName + ".amazonaws.com/" + bucketName
                    + "/ExampleObject.txt";
            s3.url(url2).method(HttpMethod.PUT).requestBody("hi there").execute();
            System.out.println("put object completed");
        }

    }
}
