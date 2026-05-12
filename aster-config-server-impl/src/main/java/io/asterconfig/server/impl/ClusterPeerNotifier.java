package io.asterconfig.server.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.asterconfig.server.protocol.AsterMessage;
import io.asterconfig.server.protocol.AsterMessageType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClusterPeerNotifier implements ClusterPeerPublisher, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ClusterPeerNotifier.class);

    private final AsterClusterProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, Channel> peerChannels = new ConcurrentHashMap<>();
    private final Set<String> warnedPeers = ConcurrentHashMap.newKeySet();
    private EventLoopGroup group;

    public ClusterPeerNotifier(AsterClusterProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        if (!properties.isEnabled() || !running.compareAndSet(false, true)) {
            return;
        }
        group = new NioEventLoopGroup();
        properties.getPeers().stream()
                .filter(this::validPeer)
                .filter(peer -> !properties.getNodeId().equals(peer.getNodeId()))
                .forEach(this::connect);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        peerChannels.values().forEach(Channel::close);
        peerChannels.clear();
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void publish(String env, String namespace, long revision) {
        if (!properties.isEnabled()) {
            return;
        }
        String payload = encode(AsterMessage.nodePublish(properties.getNodeId(), env, List.of(namespace), revision));
        peerChannels.forEach((nodeId, channel) -> {
            if (channel.isActive()) {
                channel.writeAndFlush(payload);
            }
        });
    }

    private void connect(AsterClusterProperties.Peer peer) {
        if (!running.get()) {
            return;
        }
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new IdleStateHandler(0, properties.getHeartbeatInterval().toSeconds(), 0, TimeUnit.SECONDS))
                                .addLast(new LineBasedFrameDecoder(1024 * 1024))
                                .addLast(new StringDecoder(CharsetUtil.UTF_8))
                                .addLast(new StringEncoder(CharsetUtil.UTF_8))
                                .addLast(new PeerClientHandler(peer));
                    }
                });
        bootstrap.connect(peer.getHost(), peer.getPort()).addListener(future -> {
            if (!running.get()) {
                return;
            }
            if (future.isSuccess()) {
                Channel channel = ((io.netty.channel.ChannelFuture) future).channel();
                peerChannels.put(peer.getNodeId(), channel);
                warnedPeers.remove(peer.getNodeId());
                channel.writeAndFlush(encode(AsterMessage.nodeInit(properties.getNodeId())));
                channel.closeFuture().addListener(close -> {
                    peerChannels.remove(peer.getNodeId());
                    scheduleReconnect(peer);
                });
                log.info("Connected Aster cluster peer, localNode={}, peerNode={}, host={}, port={}",
                        properties.getNodeId(), peer.getNodeId(), peer.getHost(), peer.getPort());
                return;
            }
            if (warnedPeers.add(peer.getNodeId())) {
                log.warn("Failed to connect Aster cluster peer, peerNode={}, host={}, port={}, cause={}",
                        peer.getNodeId(), peer.getHost(), peer.getPort(), future.cause().toString());
            } else {
                log.debug("Failed to connect Aster cluster peer, peerNode={}, host={}, port={}",
                        peer.getNodeId(), peer.getHost(), peer.getPort(), future.cause());
            }
            scheduleReconnect(peer);
        });
    }

    private void scheduleReconnect(AsterClusterProperties.Peer peer) {
        if (!running.get() || group == null || group.isShuttingDown()) {
            return;
        }
        group.schedule(() -> connect(peer), properties.getReconnectDelay().toMillis(), TimeUnit.MILLISECONDS);
    }

    private boolean validPeer(AsterClusterProperties.Peer peer) {
        return peer != null && peer.getNodeId() != null && peer.getHost() != null && !peer.getHost().isBlank();
    }

    private String encode(AsterMessage message) {
        try {
            return objectMapper.writeValueAsString(message) + "\n";
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode Aster cluster message", e);
        }
    }

    private class PeerClientHandler extends SimpleChannelInboundHandler<String> {

        private final AsterClusterProperties.Peer peer;

        private PeerClientHandler(AsterClusterProperties.Peer peer) {
            this.peer = peer;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String payload) throws Exception {
            AsterMessage message = objectMapper.readValue(payload, AsterMessage.class);
            if (message.type() == AsterMessageType.NODE_INIT_ACK) {
                log.info("Aster cluster peer acknowledged, localNode={}, peerNode={}",
                        properties.getNodeId(), message.nodeId());
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                ctx.writeAndFlush(encode(AsterMessage.heartbeat()));
                return;
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Aster cluster peer connection failed, peerNode={}", peer.getNodeId(), cause);
            ctx.close();
        }
    }
}
