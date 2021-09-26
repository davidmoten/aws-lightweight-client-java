package com.github.davidmoten.aws.lw.client;

import java.io.IOException;
import java.util.Collections;

import org.junit.Test;

import com.github.davidmoten.aws.lw.client.internal.util.Util;

public class ResponseInputStreamTest {
    
    @Test
    public void test() throws IOException {
        new ResponseInputStream(() ->{}, 200, Collections.emptyMap(), Util.EMPTY_INPUT_STREAM).close();
    }

}
