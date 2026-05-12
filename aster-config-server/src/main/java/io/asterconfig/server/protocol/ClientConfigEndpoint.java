package io.asterconfig.server.protocol;

public interface ClientConfigEndpoint {

    ClientConfigResponse getConfigs(ClientConfigQuery query);

    long currentRevision(ClientConfigQuery query);
}
