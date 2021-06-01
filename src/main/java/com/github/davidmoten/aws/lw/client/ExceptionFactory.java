package com.github.davidmoten.aws.lw.client;

import java.util.Optional;

import com.github.davidmoten.aws.lw.client.internal.ExceptionFactoryDefault;

@FunctionalInterface
public interface ExceptionFactory {

    /**
     * Returns a {@link RuntimeException} (or subclass) if the response error
     * condition is met (usually {@code !response.isOk()}. If no exception to be
     * thrown then returns {@code Optional.empty()}.
     * 
     * @param response response to map into exception
     * @return optional runtime exception
     */
    Optional<? extends RuntimeException> create(Response response);

    public static final ExceptionFactory DEFAULT = new ExceptionFactoryDefault();
}
