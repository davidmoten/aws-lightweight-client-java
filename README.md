# aws-lightweight-client-java
<a href="https://github.com/davidmoten/aws-lightweight-client-java/actions/workflows/ci.yml"><img src="https://github.com/davidmoten/aws-lightweight-client-java/actions/workflows/ci.yml/badge.svg"/></a><br/>
[![codecov](https://codecov.io/gh/davidmoten/aws-lightweight-client-java/branch/master/graph/badge.svg)](https://codecov.io/gh/davidmoten/aws-lightweight-client-java)<br/>
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.davidmoten/aws-lightweight-client-java/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.davidmoten/aws-lightweight-client-java)<br/>

This is a really lightweight standalone artifact (about 57K) that performs authentication (signing requests with AWS Signature Version 4) and helps you build requests against the AWS API. It includes nice concise builders, a lightweight inbuilt xml parser (to parse responses), and useful convenience methods. 

**Features**
* small standalone artifact (57K)
* concise fluent api
* signs requests with AWS Signature Version 4
* generates presigned urls
* supports throwing custom exceptions
* metadata and attributes support
* xml response parsing support
* high level of unit test coverage
* reduces Lambda cold start time considerably

**Status**: released to [Maven Central](https://search.maven.org/artifact/com.github.davidmoten/aws-lightweight-client-java)

Maven [reports](https://davidmoten.github.io/aws-lightweight-client-java/index.html) including [javadocs](https://davidmoten.github.io/aws-lightweight-client-java/apidocs/index.html)

For example with the 55K standalone artifact you can download an object from an S3 bucket:

```java
Client s3 = Client.s3()
  .regionName("ap-southeast-2")
  .accessKey(accessKey)
  .secretKey(secretKey)
  .build();

String content = s3
  .path("myBucket", "myObject.txt")
  .responseAsUtf8();
```

Here's how to create an SQS queue and send a message to that queue. This time we'll create our Client for use in a Lambda handler (credentials are picked up from environment variables):
```java
Client sqs = Client.sqs().defaultClient().build();
  
String queueUrl = sqs
    .query("Action", "CreateQueue")
    .query("QueueName", queueName(applicationName, key))
    .responseAsXml()
    .content("CreateQueueResult", "QueueUrl");
    
sqs.url(queueUrl) 
    .query("Action", "SendMessage") 
    .query("MessageBody", "hi there") 
    .execute();
```

## Lambda performance
You can see that usage is still pretty concise compared to using the AWS SDK for Java. There's a significant advantage in using the lightweight client in a Java Lambda. 

The test Lambda that I used does this:
* puts a 240K object into an S3 bucket with metadata
* creates an SQS queue 
* sends the queue a small message (16 bytes).

Using AWS SDK the shaded minimized jar deployed to Lambda is 5.1MB, with *aws-lightweight-client-java* the jar is 80K.

I deployed the lambda with 2GB memory (to get the CPU benefits from that allocation) and cold start runtime for the SDK lambda was 10.4s. The cold start runtime for the lightweight lambda was 1s! Warm invocations were average 0.3s for the SDK lambda and 0.15s for the lightweight lambda (a bit surprising!).

Aside from cold-start improvements in AWS Lambda, the small artifact size is presumably attractive also for Android developers. 

Note that testing shows that using *com.amazonaws:aws-java-sdk-s3:1.11.1032* getting an object from an S3 bucket requires loading of 4203 classes yet using *aws-lightweight-client-java:0.1.3* requires loading of 2350 classes (56%).

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

### Creating a Client
In a Lambda handler environment variables hold the credentials and session token. To pick those values up:

```java
Client s3 = Client.s3().defaultClient().build();
```
Outside of lambda you might specify your credentials explicitly:

```java
Client s3 = Client
  .s3()
  .regionName("ap-southeast-2")
  .accessKey(accessKey)
  .secretKey(secretKey)
  .build()
```
There are a number of other options that can be set when building the Client:

```java
Client iam = Client
    .serviceName("iam") 
    .regionName(regionName) 
    .accessKey(accessKey)
    .secretKey(secretKey)
    .exceptionFactory(myExceptionFactory)
    .exception(
	    x -> !x.isOk() && x.contentUtf8().contains("NonExistentPolicy"),
	    x -> new PolicyDoesNotExistException(x.contentUtf8()))
    .httpClient(myHttpClient) 
    .connectTimeoutMs(30000)
    .readTimeoutMs(120000)
    .build();
```
A client can be copied from another client to pick up same configuration (but with a different service name):

```java
Client sqs = Client.from(iam).build();
```
### Presigned URLs
Presigned URLs are generated as follows (with a specified expiry duration):

```java
String presignedUrl = 
     s3
     .path(bucketName, objectName) 
     .presignedUrl(1, TimeUnit.DAYS));
```

### S3
The code below demonstrates the following:
* create bucket
* put object with metadata
* read object and metadata
* list objects in bucket
* delete object
* delete bucket

```java
// we'll create a random bucket name
String bucketName = "temp-bucket-" + System.currentTimeMillis();

///////////////////////
// create bucket
///////////////////////

String createXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<CreateBucketConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n"
        + "   <LocationConstraint>" + regionName + "</LocationConstraint>\n"
        + "</CreateBucketConfiguration>";
s3.path(bucketName)
    .method(HttpMethod.PUT)
    .requestBody(createXml)
    .execute();

////////////////////////////
// put object with metadata
///////////////////////////

String objectName = "ExampleObject.txt";
s3
    .path(bucketName, objectName)
    .method(HttpMethod.PUT)
    .requestBody("hi there")
    .metadata("category", "something")
    .execute();

///////////////////////////////////
// read object including metadata
///////////////////////////////////

String text = s3
    .path(bucketName + "/" + objectName)
    .responseAsUtf8();

///////////////////////////////////
// read object
///////////////////////////////////

Response r = s3
    .path(bucketName, objectName)
    .response();
System.out.println("response ok=" + response.isOk());
System.out.println(r.content().length + " chars read");
System.out.println("category=" + r.metadata("category").orElse(""));

///////////////////////////////////
// list bucket objects 
///////////////////////////////////

List<String> keys = s3
    .url("https://" + bucketName + ".s3." + regionName + ".amazonaws.com")
    .query("list-type", "2")
    .responseAsXml()
    .childrenWithName("Contents")
    .stream()
    .map(x -> x.content("Key"))
    .collect(Collectors.toList());
System.out.println(keys);

///////////////////////////////////
// delete object 
///////////////////////////////////

s3.path(bucketName, objectName) 
    .method(HttpMethod.DELETE) 
    .execute();
        
///////////////////////////////////
// delete bucket 
///////////////////////////////////

s3.path(bucketName) 
	.method(HttpMethod.DELETE) 
	.execute();
```

### SQS
Here are some SQS tasks:

* create an sqs queue
* get the queue url
* place two messages on the queue
* read all messages of the queue and mark them as read
* delete the sqs queue

You'll note that most of the interactions with sqs involve using the url of the queue rather than the base service endpoint (`http://sqs.amazonaws.com`).

```java
String queueName = "MyQueue-" + System.currentTimeMillis();

///////////////////////////////////
// create queue
///////////////////////////////////

sqs.query("Action", "CreateQueue") 
    .query("QueueName", queueName) 
    .execute();

///////////////////////////////////
// get queue url
///////////////////////////////////

String queueUrl = sqs 
    .query("Action", "GetQueueUrl") 
    .query("QueueName", queueName) 
    .responseAsXml() 
    .content("GetQueueUrlResult", "QueueUrl");

///////////////////////////////////
// send a message
///////////////////////////////////

sqs.url(queueUrl) 
    .query("Action", "SendMessage") 
    .query("MessageBody", "hi there") 
    .execute();

///////////////////////////////////
// read all messages
///////////////////////////////////

List<XmlElement> list;
do {
    list = sqs.url(queueUrl)
        .query("Action", "ReceiveMessage")
        .responseAsXml()
        .child("ReceiveMessageResult")
        .children();

    list.forEach(x -> {
	    String msg = x.child("Body").content();
	    System.out.println(msg);
	    // mark message as read
	    sqs.url(queueUrl)
	            .query("Action", "DeleteMessage")
	            .query("ReceiptHandle", x.child("ReceiptHandle").content())
	            .execute();
    });
} while (!list.isEmpty());

///////////////////////////////////
// delete queue
///////////////////////////////////

sqs.url(queueUrl) 
    .query("Action", "DeleteQueue") 
    .execute();
```

### Error handling
Let's look at a simple one, reading an object in an S3 bucket.

```java
String text = s3
    .path(bucketName + "/" + objectName)
    .responseAsUtf8();
```
If the object does not exist an exception will be thrown like this:
```
com.github.davidmoten.aws.lw.client.ServiceException: statusCode=404: <?xml version="1.0" encoding="UTF-8"?>
<Error><Code>NoSuchKey</Code><Message>The specified key does not exist.</Message><Key>not-there</Key><RequestId>1TVAXX4VF5DYHJJH</RequestId><HostId>VrvGCPhExKbjuONSuX/LGw0mYSndjg3t26LNAQCKTL/i5U+cZfYa4ow3KQ1tpJdQuMH9sB4JTUk=</HostId></Error>
	at com.github.davidmoten.aws.lw.client.internal.ExceptionFactoryDefault.create(ExceptionFactoryDefault.java:17)
	at com.github.davidmoten.aws.lw.client.Request.responseAsBytes(Request.java:140)
	at com.github.davidmoten.aws.lw.client.Request.responseAsUtf8(Request.java:153)
	at com.github.davidmoten.aws.lw.client.ClientMain.main(ClientMain.java:48)
```

You can see that the AWS exception message (in xml format) is present in the error message and can be used to check for standard codes. If you were using the full AWS SDK library then it would throw a `NoSuchKeyException`. In our case we check for the presence of `NoSuchKey` in the error message.

The code below does not throw an exception when the object does not exist. However, `response.isOk()` returns false:

```java
Response r = s3
    .path(bucketName + "/" + objectName)
    .response();
System.out.println("ok=" + r.isOk() + ", statusCode=" + r.statusCode() + ", message=" + r.contentUtf8());
```

The output is:
```
ok=false, statusCode=404, message=<?xml version="1.0" encoding="UTF-8"?>
<Error><Code>NoSuchKey</Code><Message>The specified key does not exist.</Message><Key>notThere</Key><RequestId>4AAX24QZ8777FA6B</RequestId><HostId>4N1rsMjjdM7tjKSQDXNQZNH8EOqNckUsO6gRVPfcjMmHZ9APRwYJwufZOr9l1Qlinux5W537bDc=</HostId></Error>
```
### Custom exceptions
You can define what exceptions get thrown using a builder method for a `Client`:

```java
Client sqs = Client 
    .sqs()
    .defaultClient()
    .exception(
            x -> !x.isOk() && x.contentUtf8().contains("NonExistentQueue"),
            x -> new QueueDoesNotExistException(x.contentUtf8())
    .build()
```
You can add multiple exception handlers like above or you can set an `ExceptionFactory`. Any response not matching the criteria will 
throw a `ServiceException` (in those circumstances where exceptions are thrown, like `.responseAsBytes()`, `.responseAsUtf8()` and `.responseAsXml()`).

