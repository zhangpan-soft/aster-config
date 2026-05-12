package io.asterconfig.client.protocol;

import io.asterconfig.server.protocol.ClientConfigResponse;

public interface AsterConfigClient {

    ClientConfigResponse loadConfigs();

    long currentRevision(long knownRevision);
}
