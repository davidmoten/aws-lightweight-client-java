package com.github.davidmoten.aws.lw.client.internal;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.github.davidmoten.aws.lw.client.ExceptionFactory;
import com.github.davidmoten.aws.lw.client.Response;
import com.github.davidmoten.aws.lw.client.ServiceException;

public class ExceptionFactoryDefault implements ExceptionFactory{

    @Override
    public Optional<? extends RuntimeException> create(Response r) {
        if (r.isOk()) {
            return Optional.empty();
        } else {
            return Optional.of(new ServiceException(r.statusCode(),
                    new String(r.content(), StandardCharsets.UTF_8)));
        }
    }

}
