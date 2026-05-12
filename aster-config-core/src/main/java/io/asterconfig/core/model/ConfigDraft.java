package io.asterconfig.core.model;

import java.time.Instant;

public record ConfigDraft(
        String id,
        String itemId,
        OperationType operationType,
        ConfigScope scope,
        String key,
        String value,
        ValueType valueType,
        SourceFormat sourceFormat,
        boolean encrypted,
        boolean enabled,
        String description,
        DraftStatus status,
        String operator,
        Instant createdAt,
        Instant updatedAt
) {
}
