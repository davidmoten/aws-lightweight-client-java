package com.amazonaws.services.s3.sample;

public interface Credentials {
    
    String accessKey();
    String secretKey();
    
    static Credentials of(String accessKey, String secretKey) {
        return new CredentialsImpl(accessKey, secretKey);
    }

}
