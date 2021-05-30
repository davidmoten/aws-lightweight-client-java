# aws-lightweight-client-java
This is a really lightweight artifact that performs authentication (signing requests with AWS Signature Version 4) and helps you build requests against the AWS API. It includes nice concise builders, an lightweight inbuilt xml parser (to parse responses), and useful convenience methods. 

For example with a 50K standalone artifact you can do:

```java
String content = Client
  .s3() 
  .defaultClient() 
  .path("myBucket/myObject.txt") //
  .method(HttpMethod.GET) //
  .responseAsUtf8();
```

This is actually a lot more concise than using the AWS SDK for Java but moreover because the artifact is small and the number of classes loaded to perform the action is much less, the *cold start* time for a Java AWS Lambda that uses s3 is **reduced from 10s to 4s**! Sub-second cold start time latency would be great but the catch is that a lot of classes are loaded by the java platform to perform the https call to the S3 API so that's going to be hard to avoid.

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

