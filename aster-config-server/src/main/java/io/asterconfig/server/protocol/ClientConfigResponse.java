package io.asterconfig.server.protocol;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

public record ClientConfigResponse(
        String env,
        long revision,
        Map<String, TreeMap<String, String>> configs,
        Instant generatedAt
) {
}
