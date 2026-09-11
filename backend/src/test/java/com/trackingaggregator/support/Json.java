package com.trackingaggregator.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads JSON fixtures from {@code src/test/resources/fixtures}. */
public final class Json {

    private Json() {
    }

    public static String fixture(String name) {
        String path = "fixtures/" + name;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found on test classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read fixture " + path, e);
        }
    }
}
