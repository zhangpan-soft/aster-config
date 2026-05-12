package io.asterconfig.server.protocol;

import java.util.List;

public record ClientConfigQuery(
        String env,
        List<String> namespaces,
        long knownRevision
) {
}
