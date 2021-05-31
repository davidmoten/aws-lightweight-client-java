package com.github.davidmoten.aws.lw.client;

import java.util.List;
import java.util.Map;

import com.github.davidmoten.aws.lw.client.Requester.Request;
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
            // create bucket
            String bucketName = "temp-bucket-" + System.currentTimeMillis();
            String createXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<CreateBucketConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n"
                    + "   <LocationConstraint>" + regionName + "</LocationConstraint>\n"
                    + "</CreateBucketConfiguration>";
            s3.path(bucketName) //
                    .method(HttpMethod.PUT) //
                    .requestBody(createXml) //
                    .execute();

            String objectName = "ExampleObject.txt";
            Map<String, List<String>> h = s3 //
                    .path(bucketName + "/" + objectName) //
                    .method(HttpMethod.PUT) //
                    .requestBody("hi there") //
                    .header("x-amz-meta-category", "something") //
                    .response() //
                    .headers();
            System.out.println("put object completed, headers:");
            h.entrySet().stream().forEach(x -> System.out.println("  " + x));

            // read bucket object
            Response r = s3 //
                    .path(bucketName + "/" + objectName) //
                    .response();
            System.out.println(r.content().length + " chars read");
            r.headers() //
                    .entrySet() //
                    .stream() //
                    .filter(entry -> entry.getKey() != null
                            && entry.getKey().startsWith("x-amz-meta-"))
                    .map(entry -> entry.getKey().substring(11) + "=" + entry.getValue().get(0)) //
                    .forEach(System.out::println);

            // delete object
            s3.path(bucketName + "/" + objectName) //
                    .method(HttpMethod.DELETE) //
                    .execute();

            // delete bucket
            s3.path(bucketName) //
                    .method(HttpMethod.DELETE) //
                    .execute();
            System.out.println("bucket deleted");
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

            for (int i = 1; i <= 3; i++) {
                sqs.url(queueUrl) //
                        .query("Action", "SendMessage") //
                        .query("MessageBody", "hi there --> " + i) //
                        .execute();
            }

            // read all messages, print to console and delete them
            List<XmlElement> list;
            Request request = sqs //
                    .url(queueUrl) //
                    .query("Action", "ReceiveMessage");
            do {
                list = request //
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
