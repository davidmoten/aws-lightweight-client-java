# aws-lightweight-client-java
This is a really lightweight artifact that performs authentication and helps you build requests against the AWS API. It includes nice concise builders, an inbuilt xml parser (to parse responses), and useful convenience methods. 

For example with a 50K standalone artifact you can do:

```java
String content = Client
  .s3() 
  .defaultClient() 
  .path("myBucket/myObject.txt") //
  .method(HttpMethod.GET) //
  .responseAsUtf8();
```

This is actually a lot more concise than using the AWS SDK for Java but moreover because the artifact is small and the number of classes loaded to perform the action is much less, the *cold start* time for a Java AWS Lambda that uses s3 is **reduced from 10s to 4s**!

## Getting started
Add this dependency to your pom.xml:

```xml
<dependency>
    <groupId>com.github.davidmoten</groupId>
    <artifactId>aws-lightweight-client-java</artifactId>
    <version>VERSION_HERE</version>
</dependency>
```
