package io.asterconfig.server.protocol;

public enum AsterMessageType {
    INIT,
    INIT_ACK,
    HEARTBEAT,
    HEARTBEAT_ACK,
    CONFIG_CHANGE_NOTIFY,
    NODE_INIT,
    NODE_INIT_ACK,
    NODE_PUBLISH_NOTIFY
}
