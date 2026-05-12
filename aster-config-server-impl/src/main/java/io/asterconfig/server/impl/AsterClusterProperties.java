package io.asterconfig.server.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ConfigurationProperties(prefix = "aster.cluster")
public class AsterClusterProperties {

    private boolean enabled = false;
    private String nodeId = UUID.randomUUID().toString();
    private List<Peer> peers = new ArrayList<>();
    private Duration reconnectDelay = Duration.ofSeconds(2);
    private Duration heartbeatInterval = Duration.ofSeconds(15);
    private boolean publishEventPollerEnabled = true;
    private Duration publishEventPollInterval = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public List<Peer> getPeers() {
        return peers;
    }

    public void setPeers(List<Peer> peers) {
        this.peers = peers == null ? new ArrayList<>() : peers;
    }

    public Duration getReconnectDelay() {
        return reconnectDelay;
    }

    public void setReconnectDelay(Duration reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public boolean isPublishEventPollerEnabled() {
        return publishEventPollerEnabled;
    }

    public void setPublishEventPollerEnabled(boolean publishEventPollerEnabled) {
        this.publishEventPollerEnabled = publishEventPollerEnabled;
    }

    public Duration getPublishEventPollInterval() {
        return publishEventPollInterval;
    }

    public void setPublishEventPollInterval(Duration publishEventPollInterval) {
        this.publishEventPollInterval = publishEventPollInterval;
    }

    public static class Peer {

        private String nodeId;
        private String host;
        private int port = 9088;

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}
