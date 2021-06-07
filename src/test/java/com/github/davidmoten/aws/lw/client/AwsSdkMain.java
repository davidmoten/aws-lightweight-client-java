package com.github.davidmoten.aws.lw.client;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

public class AwsSdkMain {

    public static void main(String[] args) {
        String s = "hi there";
        String bucketName = "amsa-xml-in";
        String queueName = "amsa-xml-in";

        AWSCredentialsProvider c = new SystemPropertiesCredentialsProvider();
        AmazonS3 s3 = AmazonS3Client.builder().withCredentials(c).withRegion("ap-southeast-2")
                .build();

    }
}
