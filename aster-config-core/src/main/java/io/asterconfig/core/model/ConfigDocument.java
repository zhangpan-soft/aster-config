package io.asterconfig.core.model;

import java.time.Instant;

public record ConfigDocument(
        ConfigScope scope,
        SourceFormat sourceFormat,
        String sourceContent,
        long revision,
        String operator,
        Instant updatedAt
) {
}
