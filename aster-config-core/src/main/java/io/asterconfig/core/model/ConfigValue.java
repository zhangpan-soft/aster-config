package io.asterconfig.core.model;

public record ConfigValue(String value, ValueType valueType) {

    public static ConfigValue of(String value) {
        return new ConfigValue(value, ValueType.infer(value));
    }
}
