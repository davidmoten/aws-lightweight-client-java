package com.github.davidmoten.aws.lw.client;

public class LightweightMain {
    
    public static void main(String[] args) {
        Client s3 = Client.s3().region("ap-southeast-2").credentialsFromSystemProperties().build();
        System.out.println(s3.path("amsa-xml-in", "ExampleObject.txt").responseAsUtf8());
    }

}
