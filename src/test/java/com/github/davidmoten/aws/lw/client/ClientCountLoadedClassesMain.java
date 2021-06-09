package com.github.davidmoten.aws.lw.client;

public class ClientCountLoadedClassesMain {
    
    public static void main(String[] args) {
        // run with -verbose:class
        String regionName = "ap-southeast-2";
        String accessKey = System.getProperty("accessKey");
        String secretKey = System.getProperty("secretKey");

        Credentials credentials = Credentials.of(accessKey, secretKey);
        Client s3 = Client //
                .s3() //
                .regionName(regionName) //
                .credentials(credentials) //
                .build();
        System.out.println(s3.path("amsa-xml-in", "ExampleObject.txt").responseAsUtf8());
        System.out.println("2350 classes loaded");
    }

}
