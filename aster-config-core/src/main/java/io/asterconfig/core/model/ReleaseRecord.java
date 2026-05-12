package io.asterconfig.core.model;

import java.time.Instant;

public record ReleaseRecord(
        String id,
        ConfigScope scope,
        long revision,
        String releaseNote,
        String operator,
        Instant publishedAt
) {
}
