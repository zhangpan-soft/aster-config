package io.asterconfig.server.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.asterconfig.server.protocol.AsterMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSubscriptionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ClientSubscriptionRegistry.class);

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<ChannelId, ClientSubscription> subscriptions = new ConcurrentHashMap<>();

    public ClientSubscriptionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(Channel channel, String env, List<String> namespaces) {
        List<String> normalized = normalizeNamespaces(namespaces);
        subscriptions.put(channel.id(), new ClientSubscription(channel, env, Set.copyOf(normalized)));
        log.info("Registered Aster client subscription, remote={}, env={}, namespaces={}",
                channel.remoteAddress(), env, normalized);
    }

    public void unregister(Channel channel) {
        subscriptions.remove(channel.id());
    }

    public void publish(String env, String namespace, long revision) {
        AsterMessage message = AsterMessage.configChange(env, List.of(namespace), revision);
        String payload = encode(message);
        subscriptions.values().forEach(subscription -> {
            if (!subscription.matches(env, namespace)) {
                return;
            }
            if (!subscription.channel().isActive()) {
                subscriptions.remove(subscription.channel().id());
                return;
            }
            subscription.channel().writeAndFlush(payload);
        });
    }

    public int size() {
        return subscriptions.size();
    }

    private List<String> normalizeNamespaces(List<String> namespaces) {
        if (namespaces == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        namespaces.stream()
                .map(String::trim)
                .filter(namespace -> !namespace.isBlank())
                .forEach(result::add);
        return result;
    }

    private String encode(AsterMessage message) {
        try {
            return objectMapper.writeValueAsString(message) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode Aster netty message", e);
        }
    }

    private record ClientSubscription(Channel channel, String env, Set<String> namespaces) {

        boolean matches(String publishEnv, String namespace) {
            return env.equals(publishEnv) && namespaces.contains(namespace);
        }
    }
}
