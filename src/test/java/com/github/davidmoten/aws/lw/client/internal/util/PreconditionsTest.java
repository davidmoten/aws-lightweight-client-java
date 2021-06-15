package com.github.davidmoten.aws.lw.client.internal.util;

import org.junit.Test;

import com.github.davidmoten.junit.Asserts;

public class PreconditionsTest {

    @Test
    public void isUtilityClass() {
        Asserts.assertIsUtilityClass(Preconditions.class);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void wantIAEnotNPE() {
        Preconditions.checkNotNull(null, "hey!");
    }

}
