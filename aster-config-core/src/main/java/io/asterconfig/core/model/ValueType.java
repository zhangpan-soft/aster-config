package io.asterconfig.core.model;

public enum ValueType {
    STRING,
    NUMBER,
    BOOLEAN,
    NULL;

    public static ValueType infer(String value) {
        if (value == null) {
            return NULL;
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
            return BOOLEAN;
        }
        if (normalized.matches("-?\\d+(\\.\\d+)?")) {
            return NUMBER;
        }
        return STRING;
    }
}
