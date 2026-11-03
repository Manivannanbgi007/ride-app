package com.fooddelivery.e2e.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Where the platform runs and how to sign in as its first admin. Each setting comes from a system property
 * (-De2e.baseUrl=...), else an environment variable, else the project's .env file (local runs), else the default.
 */
public final class E2EConfig {

    private static final Path DOT_ENV = Path.of("..", ".env");

    private E2EConfig() {
    }

    /** The API Gateway: every call of the tests goes through it, like a client's. */
    public static String baseUrl() {
        return setting("e2e.baseUrl", "E2E_BASE_URL", "http://localhost:8080");
    }

    /** Mailpit's web API: the e-mails the platform sent (development stack only). */
    public static String mailpitUrl() {
        return setting("e2e.mailpitUrl", "E2E_MAILPIT_URL", "http://localhost:8025");
    }

    public static String prometheusUrl() {
        return setting("e2e.prometheusUrl", "E2E_PROMETHEUS_URL", "http://localhost:9090");
    }

    public static String lokiUrl() {
        return setting("e2e.lokiUrl", "E2E_LOKI_URL", "http://localhost:3100");
    }

    public static String tempoUrl() {
        return setting("e2e.tempoUrl", "E2E_TEMPO_URL", "http://localhost:3200");
    }

    public static String adminEmail() {
        return setting("e2e.adminEmail", "BOOTSTRAP_ADMIN_EMAIL", "admin@fooddelivery.local");
    }

    public static String adminPassword() {
        return setting("e2e.adminPassword", "BOOTSTRAP_ADMIN_PASSWORD", "ChangeMe123");
    }

    private static String setting(String property, String variable, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(variable);
        }
        if (value == null || value.isBlank()) {
            value = fromDotEnv(variable).orElse(fallback);
        }
        return value.trim();
    }

    private static Optional<String> fromDotEnv(String variable) {
        if (!Files.isRegularFile(DOT_ENV)) {
            return Optional.empty();
        }
        try {
            return Files.readAllLines(DOT_ENV).stream()
                    .filter(line -> line.startsWith(variable + "="))
                    .map(line -> line.substring(variable.length() + 1))
                    .filter(value -> !value.isBlank())
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
