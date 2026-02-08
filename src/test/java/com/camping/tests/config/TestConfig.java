package com.camping.tests.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Properties;

public class TestConfig {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = TestConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("Error: config.properties file not found in classpath.");
            }
            properties.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Error loading config.properties file: " + ex.getMessage(), ex);
        }
    }

    public static String getProperty(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new RuntimeException("Property key cannot be null or empty.");
        }

        String normalizedKey = normalizeKey(key);
        String envValue = System.getenv(normalizedKey);
        if(envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        String sysValue = System.getProperty(normalizedKey);
        if (sysValue != null && !sysValue.isEmpty()) {
            return sysValue;
        }

        String value = properties.getProperty(normalizedKey);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        throw new RuntimeException("Required property '" + key + "' (normalized to '" + normalizedKey + "') not found in system properties or config.properties.");
    }

    public static String getProperty(String key, String defaultValue) {
        try {
            return getProperty(key);
        } catch (RuntimeException e) {
            if (defaultValue != null) return defaultValue;
            throw e;
        }
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase().replace('-', '.').replace('_', '.');
    }

    public static String getKioskBaseUrl() {
        return getProperty("kiosk.base.url");
    }

    public static String getAdminBaseUrl() {
        return getProperty("admin.base.url");
    }

    public static String getReservationBaseUrl() {
        return getProperty("reservation.base.url");
    }

    public static String getPaymentBaseUrl() {
        return getProperty("payment.base.url");
    }

    public static String getPaymentMockHost() {
        try {
            return URI.create(getPaymentBaseUrl()).getHost();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid or malformed payment.base.url in config.properties: " + getPaymentBaseUrl(), e);
        }
    }

    public static int getPaymentMockPort() {
        try {
            URI uri = URI.create(getPaymentBaseUrl());
            int port = uri.getPort();

            if (port == -1) {
                return uri.toURL().getDefaultPort();
            }

            return port;
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new RuntimeException("Invalid or malformed payment.base.url in config.properties: " + getPaymentBaseUrl(), e);
        }
    }
}