# aws-lightweight-client-java
This is a really lightweight artifact that performs authentication (signing requests with AWS Signature Version 4) and helps you build requests against the AWS API. It includes nice concise builders, a lightweight inbuilt xml parser (to parse responses), and useful convenience methods. 

For example with a 50K standalone artifact you can do:

```java
String content = Client
  .s3() 
  .defaultClient() 
  .path("myBucket/myObject.txt") //
  .responseAsUtf8();
```

This is actually a lot more concise than using the AWS SDK for Java but moreover because the artifact is small and the number of classes loaded to perform the action is much less, the *cold start* time for a Java AWS Lambda that uses s3 is **reduced from 10s to 4s**! Sub-second cold start time latency would be great but the catch is that a lot of classes are loaded by the java platform to perform the https call to the S3 API so that's going to be hard to avoid. In fact my testing shows that without any https calls at all a lambda can cold start in <1s (but will have presumably pretty limited functionality)!

## Getting started
Add this dependency to your pom.xml:

```xml
<dependency>
    <groupId>com.github.davidmoten</groupId>
    <artifactId>aws-lightweight-client-java</artifactId>
    <version>VERSION_HERE</version>
</dependency>
```

## Usage

To perform actions against the API you do need to know what methods exist and the parameters for those methods. This library is lightweight because it doesn't include a mass of generated classes from the API so you'll need to check the AWS API documentation to get that information. For example the API docs for S3 is [here](https://docs.aws.amazon.com/AmazonS3/latest/API/Welcome.html).

Let's try some other tasks. Here are some SQS tasks:

* create an sqs queue
* get the queue url
* place two messages on the queue
* read all messages of the queue and mark them as read
* delete the sqs queue

You'll note that most of the interactions with sqs involve using the url of the queue rather than the base service endpoint (`http://sqs.amazonaws.com`).

```java
String queueName = "MyQueue-" + System.currentTimeMillis();

// create queue
sqs.query("Action", "CreateQueue") 
    .query("QueueName", queueName) 
    .execute();

// get queue url
String queueUrl = sqs 
    .query("Action", "GetQueueUrl") 
    .query("QueueName", queueName) 
    .responseAsXml() 
    .content("GetQueueUrlResult", "QueueUrl");

// send a message
sqs.url(queueUrl) 
    .query("Action", "SendMessage") 
    .query("MessageBody", "hi there") 
    .execute();

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

// delete queue
sqs.url(queueUrl) 
    .query("Action", "DeleteQueue") 
    .execute();
```


