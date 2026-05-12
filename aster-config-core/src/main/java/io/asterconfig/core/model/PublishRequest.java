package io.asterconfig.core.model;

public record PublishRequest(
        ConfigScope scope,
        String releaseNote,
        String operator
) {
}
