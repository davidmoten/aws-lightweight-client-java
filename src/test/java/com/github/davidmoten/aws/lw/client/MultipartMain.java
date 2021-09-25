package com.github.davidmoten.aws.lw.client;

import java.io.File;

public class MultipartMain {

    public static void main(String[] args) {
        String regionName = "ap-southeast-2";
        String accessKey = System.getProperty("accessKey");
        String secretKey = System.getProperty("secretKey");

        Credentials credentials = Credentials.of(accessKey, secretKey);
        Client sqs = Client //
                .sqs() //
                .region(regionName) //
                .credentials(credentials) //
                .build();
        Client s3 = Client.s3().from(sqs).build();

        Multipart //
                .s3(s3) //
                .bucket("moten-fixes") //
                .key("part001.json") //
                .upload(new File("/home/dave/part001.json"));
        System.out.println("completed upload");
    }

}
