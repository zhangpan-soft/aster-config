package io.asterconfig.core.model;

import java.time.Instant;

public record ConfigItem(
        String id,
        ConfigScope scope,
        String key,
        String value,
        ValueType valueType,
        SourceFormat sourceFormat,
        boolean encrypted,
        boolean enabled,
        String description,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
}
