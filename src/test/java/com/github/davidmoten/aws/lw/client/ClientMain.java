package com.github.davidmoten.aws.lw.client;

import java.util.List;
import java.util.Map;

public final class ClientMain {

    public static void main(String[] args) {
        String regionName = "ap-southeast-2";
        String accessKey = System.getProperty("accessKey");
        String secretKey = System.getProperty("secretKey");

        Credentials credentials = Credentials.of(accessKey, secretKey);
        {
            Client sqs = Client //
                    .sqs() //
                    .regionName(regionName) //
                    .credentials(credentials);

            String queueUrl = sqs //
                    .query("Action", "GetQueueUrl") //
                    .query("QueueName", "amsa-xml-in") //
                    .method(HttpMethod.GET) //
                    .responseAsXml() //
                    .content("GetQueueUrlResult", "QueueUrl");
            System.out.println(queueUrl);
        }
        {
            // read bucket object
            Client s3 = Client.s3() //
                    .regionName(regionName) //
                    .credentials(credentials);
            String bucketName = "amsa-xml-in";
            s3 //
                    .path(bucketName + "/ExampleObject.txt") //
                    .method(HttpMethod.GET) //
                    .responseAsUtf8(x -> System.out.println(x.length() + " chars read"));

            // put data into bucket object
            Map<String, List<String>> h = s3 //
                    .path(bucketName + "/ExampleObject.txt") //
                    .method(HttpMethod.PUT) //
                    .requestBody("hi there") //
                    .response() //
                    .headers();
            System.out.println("put object completed, headers=" );
            h.entrySet().stream().forEach(x -> System.out.println("  " + x));
        }
    }
}
