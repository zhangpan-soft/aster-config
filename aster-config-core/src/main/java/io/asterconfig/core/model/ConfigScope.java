package io.asterconfig.core.model;

import java.util.Objects;

public record ConfigScope(String env, String namespace) {

    public ConfigScope {
        env = requireText(env, "env");
        namespace = requireText(namespace, "namespace");
    }

    public String identity() {
        return env + ":" + namespace;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
