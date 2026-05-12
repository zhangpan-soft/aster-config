package io.asterconfig.server.impl;

import io.asterconfig.core.model.PublishResult;
import io.asterconfig.core.spi.ConfigPublishListener;

public class NettyConfigPublishListener implements ConfigPublishListener {

    private final ClientSubscriptionRegistry subscriptionRegistry;
    private final ClusterPeerPublisher clusterPeerPublisher;

    public NettyConfigPublishListener(ClientSubscriptionRegistry subscriptionRegistry, ClusterPeerPublisher clusterPeerPublisher) {
        this.subscriptionRegistry = subscriptionRegistry;
        this.clusterPeerPublisher = clusterPeerPublisher;
    }

    @Override
    public void onPublish(PublishResult result) {
        subscriptionRegistry.publish(result.scope().env(), result.scope().namespace(), result.revision());
        clusterPeerPublisher.publish(result.scope().env(), result.scope().namespace(), result.revision());
    }
}
