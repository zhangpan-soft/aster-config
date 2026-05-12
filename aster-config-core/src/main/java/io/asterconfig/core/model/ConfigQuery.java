package io.asterconfig.core.model;

public record ConfigQuery(
        ConfigScope scope,
        String keyword,
        boolean includeDisabled
) {
    public static ConfigQuery scope(ConfigScope scope) {
        return new ConfigQuery(scope, null, false);
    }
}
