package com.amazonaws.services.s3.sample;

public final class Service {
    public Service sqs() {
        return new Service("sqs");
    }
    
    private final String name;
    
    private Service(String name) {
        this.name = name;
    }
}
