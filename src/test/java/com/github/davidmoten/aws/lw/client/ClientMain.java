package com.github.davidmoten.aws.lw.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
                .credentials(credentials) //
                .build();
        Client s3 = Client.s3().from(sqs).build();
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
                    .metadata("category", "something") //
                    .response() //
                    .headers();
            System.out.println("put object completed, headers:");
            h.entrySet().stream().forEach(x -> System.out.println("  " + x));

            try {
                s3.path(bucketName + "/" + "not-there") //
                        .responseAsUtf8();
            } catch (Throwable e) {
                e.printStackTrace(System.out);
            }

            try {
                Client.s3() //
                        .regionName(regionName) //
                        .credentials(credentials) //
                        .exception(r -> !r.isOk(), r -> new NoSuchKeyException(r.contentUtf8()))
                        .build() //
                        .path(bucketName + "/" + "not-there") //
                        .responseAsUtf8();
            } catch (NoSuchKeyException e) {
                e.printStackTrace(System.out);
            }

            {
                Response r = s3.path(bucketName + "/" + "notThere").response();
                System.out.println("ok=" + r.isOk() + ", statusCode=" + r.statusCode()
                        + ", message=" + r.contentUtf8());
            }
            {
                // read bucket object
                String text = s3.path(bucketName + "/" + objectName).responseAsUtf8();
                System.out.println(text);
                System.out.println("presignedUrl="
                        + s3.path(bucketName + "/" + objectName).presignedUrl(1, TimeUnit.DAYS));
            }
            {
                // read bucket object with metadata
                Response r = s3 //
                        .path(bucketName + "/" + objectName) //
                        .response();
                System.out.println(r.content().length + " chars read");
                r //
                        .metadata() //
                        .entrySet() //
                        .stream() //
                        .map(x -> x.getKey() + "=" + x.getValue()) //
                        .forEach(System.out::println);

                System.out.println("category[0]=" + r.metadata("category").orElse(""));
            }

            List<String> keys = s3 //
                    .url("https://" + bucketName + ".s3." + regionName + ".amazonaws.com") //
                    .query("list-type", "2") //
                    .responseAsXml() //
                    .childrenWithName("Contents") //
                    .stream() //
                    .map(x -> x.content("Key")) //
                    .collect(Collectors.toList());

            System.out.println(keys);

            // delete object
            s3.path(bucketName + "/" + objectName) //
                    .method(HttpMethod.DELETE) //
                    .execute();

            // delete bucket
            s3.path(bucketName) //
                    .method(HttpMethod.DELETE) //
                    .execute();
            System.out.println("bucket deleted");

            System.out.println("all actions complete on s3");
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
