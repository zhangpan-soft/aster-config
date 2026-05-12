package io.asterconfig.core.model;

import java.time.Instant;

public record PublishResult(
        ConfigScope scope,
        long revision,
        int changedItems,
        Instant publishedAt
) {
}
