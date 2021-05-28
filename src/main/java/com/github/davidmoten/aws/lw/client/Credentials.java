package com.github.davidmoten.aws.lw.client;

public interface Credentials {
    
    String accessKey();
    String secretKey();
    
    static Credentials of(String accessKey, String secretKey) {
        return new CredentialsImpl(accessKey, secretKey);
    }

}
