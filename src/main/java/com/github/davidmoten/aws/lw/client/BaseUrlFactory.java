package com.github.davidmoten.aws.lw.client;

import java.util.Optional;

@FunctionalInterface
public interface BaseUrlFactory {

    String create(String serviceName, Optional<String> region);

    public static final BaseUrlFactory DEFAULT = (serviceName, region) -> "https://" //
            + serviceName //
            + region.map(x -> "." + x).orElse("") //
            + ".amazonaws.com/";

}
