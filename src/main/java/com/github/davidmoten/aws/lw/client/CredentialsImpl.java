package com.github.davidmoten.aws.lw.client;

public final class CredentialsImpl implements Credentials {

    private final String accessKey;
    private final String secretKey;
    
    public CredentialsImpl(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }
    
    @Override
    public String accessKey() {
        // TODO Auto-generated method stub
        return accessKey;
    }

    @Override
    public String secretKey() {
        return secretKey;
    }

}
