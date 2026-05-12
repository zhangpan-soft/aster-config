package io.asterconfig.server.impl;

public interface ClusterPeerPublisher {

    void publish(String env, String namespace, long revision);
}
