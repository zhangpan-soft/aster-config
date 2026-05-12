package io.asterconfig.server.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.asterconfig.server.protocol.AsterMessage;
import io.asterconfig.server.protocol.AsterMessageType;
import io.asterconfig.server.protocol.ClientConfigEndpoint;
import io.asterconfig.server.protocol.ClientConfigQuery;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class AsterNettyServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(AsterNettyServerHandler.class);

    private final ObjectMapper objectMapper;
    private final ClientSubscriptionRegistry subscriptionRegistry;
    private final ClientConfigEndpoint clientConfigEndpoint;
    private final AsterClusterProperties clusterProperties;

    public AsterNettyServerHandler(
            ObjectMapper objectMapper,
            ClientSubscriptionRegistry subscriptionRegistry,
            ClientConfigEndpoint clientConfigEndpoint,
            AsterClusterProperties clusterProperties
    ) {
        this.objectMapper = objectMapper;
        this.subscriptionRegistry = subscriptionRegistry;
        this.clientConfigEndpoint = clientConfigEndpoint;
        this.clusterProperties = clusterProperties;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String payload) throws Exception {
        AsterMessage message = objectMapper.readValue(payload, AsterMessage.class);
        if (message.type() == AsterMessageType.INIT) {
            subscriptionRegistry.register(ctx.channel(), message.env(), message.namespaces());
            long revision = clientConfigEndpoint.currentRevision(
                    new ClientConfigQuery(message.env(), message.namespaces(), 0));
            ctx.writeAndFlush(objectMapper.writeValueAsString(
                    AsterMessage.initAck(message.env(), message.namespaces(), revision)) + "\n");
            return;
        }
        if (message.type() == AsterMessageType.HEARTBEAT) {
            ctx.writeAndFlush(objectMapper.writeValueAsString(AsterMessage.heartbeatAck()) + "\n");
            return;
        }
        if (message.type() == AsterMessageType.NODE_INIT) {
            ctx.writeAndFlush(objectMapper.writeValueAsString(AsterMessage.nodeInitAck(clusterProperties.getNodeId())) + "\n");
            log.info("Aster cluster peer registered, remote={}, peerNode={}", ctx.channel().remoteAddress(), message.nodeId());
            return;
        }
        if (message.type() == AsterMessageType.NODE_PUBLISH_NOTIFY) {
            if (message.namespaces() != null) {
                message.namespaces().forEach(namespace ->
                        subscriptionRegistry.publish(message.env(), namespace, message.revision()));
            }
            log.info("Received Aster cluster publish notification, remote={}, peerNode={}, env={}, namespaces={}, revision={}",
                    ctx.channel().remoteAddress(), message.nodeId(), message.env(), message.namespaces(), message.revision());
            return;
        }
        log.warn("Ignored unsupported Aster netty message, remote={}, type={}", ctx.channel().remoteAddress(), message.type());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        subscriptionRegistry.unregister(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            subscriptionRegistry.unregister(ctx.channel());
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        subscriptionRegistry.unregister(ctx.channel());
        log.warn("Aster netty client connection failed, remote={}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
