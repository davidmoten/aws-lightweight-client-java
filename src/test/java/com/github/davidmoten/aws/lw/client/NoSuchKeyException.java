package com.github.davidmoten.aws.lw.client;

public class NoSuchKeyException extends RuntimeException {

    private static final long serialVersionUID = -1589744530315669585L;

    public NoSuchKeyException(String message) {
        super(message);
    }

}
