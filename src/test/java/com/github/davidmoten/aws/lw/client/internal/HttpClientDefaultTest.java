package com.github.davidmoten.aws.lw.client.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.github.davidmoten.aws.lw.client.ResponseInputStream;

public class HttpClientDefaultTest {

    @Test
    public void testGetInputStreamThrows() throws IOException {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        when(connection.getInputStream()).thenThrow(IOException.class);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(connection.getResponseCode()).thenReturn(200);
        try {
            HttpClientDefault.request(connection, new byte[0]);
            Assert.fail();
        } catch (UncheckedIOException e) {
            // expected
        }
        Mockito.verify(connection, Mockito.times(1)).disconnect();
    }

    @Test
    public void testDisconnectThrows() throws IOException {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        when(connection.getInputStream()).thenThrow(IOException.class);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(connection.getResponseCode()).thenReturn(200);
        Mockito.doThrow(RuntimeException.class).when(connection).disconnect();
        try {
            HttpClientDefault.request(connection, new byte[0]);
            Assert.fail();
        } catch (UncheckedIOException e) {
            // expected
        }
        Mockito.verify(connection, Mockito.times(1)).disconnect();
    }

    @Test
    public void testGetInputStreamReturnsNull() throws IOException {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        when(connection.getInputStream()).thenReturn(null);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(connection.getResponseCode()).thenReturn(200);
        try (ResponseInputStream response = HttpClientDefault.request(connection, new byte[0])) {
            assertEquals(200, response.statusCode());
            assertEquals(-1, response.read());
            assertTrue(response.headers().isEmpty());
        }
    }
    
    @Test
    public void testIsOKWhenNotOk() throws IOException {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        when(connection.getInputStream()).thenReturn(null);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(connection.getResponseCode()).thenReturn(500);
        try (ResponseInputStream response = HttpClientDefault.request(connection, new byte[0])) {
            assertEquals(500, response.statusCode());
            assertEquals(-1, response.read());
            assertTrue(response.headers().isEmpty());
        }
    }
    
    @Test
    public void testIsOKWhenNotOkStatusCodeLessThan200() throws IOException {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        when(connection.getInputStream()).thenReturn(null);
        when(connection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(connection.getResponseCode()).thenReturn(100);
        try (ResponseInputStream response = HttpClientDefault.request(connection, new byte[0])) {
            assertEquals(100, response.statusCode());
            assertEquals(-1, response.read());
            assertTrue(response.headers().isEmpty());
        }
    }

}
