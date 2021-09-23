package com.github.davidmoten.aws.lw.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.InvalidObjectStateException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.utils.IoUtils;

public class AwsSdkV2Main {
    
    public static void main(String[] args) throws NoSuchKeyException, InvalidObjectStateException, S3Exception, AwsServiceException, SdkClientException, IOException {
        S3Client client = S3Client.builder()
                .region(Region.AP_SOUTHEAST_2)
                .credentialsProvider(SystemPropertyCredentialsProvider.create()) //
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
        String r = IoUtils.toUtf8String(client.getObject(GetObjectRequest.builder().bucket("amsa-xml-in").key("ExampleObject.txt").build()));
        System.out.println(r);
        CompletedPart part = CompletedPart.builder().eTag("et123").partNumber(1).build();
        Collection<CompletedPart> parts = Arrays.asList(part);
        CompletedMultipartUpload m = CompletedMultipartUpload.builder().parts(parts).build();
        //        client.putObject(PutObjectRequest.builder().bucket("amsa-xml-in").key("ExampleObject.txt").build(), //
//                RequestBody.fromString("hi there"));
        CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder().bucket("amsa-xml-in").key("mykey").uploadId("abc") //
                .multipartUpload(m).build();
        client.completeMultipartUpload(request);
    }

}
