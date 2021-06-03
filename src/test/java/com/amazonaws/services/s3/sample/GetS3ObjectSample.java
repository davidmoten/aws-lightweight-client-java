package com.amazonaws.services.s3.sample;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.github.davidmoten.aws.lw.client.internal.Clock;
import com.github.davidmoten.aws.lw.client.internal.auth.Aws4SignerBase;
import com.github.davidmoten.aws.lw.client.internal.auth.Aws4SignerForAuthorizationHeader;
import com.github.davidmoten.aws.lw.client.internal.util.Util;

/**
 * Samples showing how to GET an object from Amazon S3 using Signature V4
 * authorization.
 */
public class GetS3ObjectSample {
    
    public static void main(String[] args) {
        getS3Object("amsa-xml-in", "ap-southeast-2", System.getProperty("accessKey"), System.getProperty("secretKey"));
    }
    
    /**
     * Request the content of the object '/ExampleObject.txt' from the given
     * bucket in the given region using virtual hosted-style object addressing.
     */
    public static void getS3Object(String bucketName, String regionName, String awsAccessKey, String awsSecretKey) {
        System.out.println("*******************************************************");
        System.out.println("*  Executing sample 'GetObjectUsingHostedAddressing'  *");
        System.out.println("*******************************************************");
        
        // the region-specific endpoint to the target object expressed in path style
        URL endpointUrl = Util.toUrl("https://" + bucketName + ".s3.amazonaws.com/driveItem.txt");
        
        // for a simple GET, we have no body so supply the precomputed 'empty' hash
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-amz-content-sha256", Aws4SignerBase.EMPTY_BODY_SHA256);
        
        Aws4SignerForAuthorizationHeader signer = new Aws4SignerForAuthorizationHeader(
                endpointUrl, "GET", "s3", regionName);
        String authorization = signer.computeSignature(Clock.DEFAULT, headers, 
                                                       null, // no query parameters
                                                       Aws4SignerBase.EMPTY_BODY_SHA256, 
                                                       awsAccessKey, 
                                                       awsSecretKey);
                
        // place the computed signature into a formatted 'Authorization' header
        // and call S3
        headers.put("Authorization", authorization);
        String response = HttpUtils.invokeHttpRequest(endpointUrl, "GET", headers, null);
        System.out.println("--------- Response content ---------");
        System.out.println(response);
        System.out.println("------------------------------------");
    }
}
