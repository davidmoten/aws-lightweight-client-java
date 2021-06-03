package com.github.davidmoten.aws.lw.client.internal;

@FunctionalInterface
public interface Clock {

    long time();
    
    Clock DEFAULT = new ClockDefault();
}
