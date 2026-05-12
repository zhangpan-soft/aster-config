package io.asterconfig.server.protocol;

import java.util.List;

public record AsterMessage(
        AsterMessageType type,
        String nodeId,
        String env,
        List<String> namespaces,
        long revision
) {

    public static AsterMessage init(String env, List<String> namespaces) {
        return new AsterMessage(AsterMessageType.INIT, null, env, namespaces, 0);
    }

    public static AsterMessage initAck(String env, List<String> namespaces, long revision) {
        return new AsterMessage(AsterMessageType.INIT_ACK, null, env, namespaces, revision);
    }

    public static AsterMessage heartbeat() {
        return new AsterMessage(AsterMessageType.HEARTBEAT, null, null, List.of(), 0);
    }

    public static AsterMessage heartbeatAck() {
        return new AsterMessage(AsterMessageType.HEARTBEAT_ACK, null, null, List.of(), 0);
    }

    public static AsterMessage configChange(String env, List<String> namespaces, long revision) {
        return new AsterMessage(AsterMessageType.CONFIG_CHANGE_NOTIFY, null, env, namespaces, revision);
    }

    public static AsterMessage nodeInit(String nodeId) {
        return new AsterMessage(AsterMessageType.NODE_INIT, nodeId, null, List.of(), 0);
    }

    public static AsterMessage nodeInitAck(String nodeId) {
        return new AsterMessage(AsterMessageType.NODE_INIT_ACK, nodeId, null, List.of(), 0);
    }

    public static AsterMessage nodePublish(String nodeId, String env, List<String> namespaces, long revision) {
        return new AsterMessage(AsterMessageType.NODE_PUBLISH_NOTIFY, nodeId, env, namespaces, revision);
    }
}
