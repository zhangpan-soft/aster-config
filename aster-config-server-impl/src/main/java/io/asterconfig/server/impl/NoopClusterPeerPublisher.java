package io.asterconfig.server.impl;

public class NoopClusterPeerPublisher implements ClusterPeerPublisher {

    @Override
    public void publish(String env, String namespace, long revision) {
    }
}
