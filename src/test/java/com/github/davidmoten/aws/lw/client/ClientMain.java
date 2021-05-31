package com.github.davidmoten.aws.lw.client;

import java.util.List;
import java.util.Map;

import com.github.davidmoten.xml.XmlElement;

public final class ClientMain {

    public static void main(String[] args) throws InterruptedException {
        String regionName = "ap-southeast-2";
        String accessKey = System.getProperty("accessKey");
        String secretKey = System.getProperty("secretKey");

        Credentials credentials = Credentials.of(accessKey, secretKey);
        Client sqs = Client //
                .sqs() //
                .regionName(regionName) //
                .credentials(credentials);
        Client s3 = Client.s3().from(sqs);
        {
            String queueUrl = sqs //
                    .query("Action", "GetQueueUrl") //
                    .query("QueueName", "amsa-xml-in") //
                    .responseAsXml() //
                    .content("GetQueueUrlResult", "QueueUrl");
            System.out.println(queueUrl);
        }
        {
            // read bucket object

            String bucketName = "amsa-xml-in";
            s3 //
                    .path(bucketName + "/ExampleObject.txt") //
                    .responseAsUtf8(x -> System.out.println(x.length() + " chars read"));

            // put data into bucket object
            Map<String, List<String>> h = s3 //
                    .path(bucketName + "/ExampleObject.txt") //
                    .method(HttpMethod.PUT) //
                    .requestBody("hi there") //
                    .response() //
                    .headers();
            System.out.println("put object completed, headers:");
            h.entrySet().stream().forEach(x -> System.out.println("  " + x));
        }

        {
            String queueName = "MyQueue-" + System.currentTimeMillis();

            sqs.query("Action", "CreateQueue") //
                    .query("QueueName", queueName) //
                    .execute();

            String queueUrl = sqs //
                    .query("Action", "GetQueueUrl") //
                    .query("QueueName", queueName) //
                    .responseAsXml() //
                    .content("GetQueueUrlResult", "QueueUrl");

            for (int i = 1; i <= 2; i++) {
                sqs.url(queueUrl) //
                        .query("Action", "SendMessage") //
                        .query("MessageBody", "hi there " + i) //
                        .execute();
            }

            // read all messages, print to console and delete them
            List<XmlElement> list;
            do {
                list = sqs.url(queueUrl) //
                        .query("Action", "ReceiveMessage") //
                        .responseAsXml() //
                        .child("ReceiveMessageResult") //
                        .children();

                list.forEach(x -> {
                    String msg = x.child("Body").content();
                    System.out.println(msg);
                    // mark message as read
                    sqs.url(queueUrl) //
                            .query("Action", "DeleteMessage") //
                            .query("ReceiptHandle", x.child("ReceiptHandle").content()) //
                            .execute();
                });
            } while (!list.isEmpty());

            sqs.url(queueUrl) //
                    .query("Action", "DeleteQueue") //
                    .execute();

            System.out.println("all actions complete on " + queueUrl);
        }
    }
}
