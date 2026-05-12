package io.asterconfig.client.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.asterconfig.client.protocol.AsterClientProperties;
import io.asterconfig.server.protocol.AsterMessage;
import io.asterconfig.server.protocol.AsterMessageType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
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

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class NettyAsterConfigClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NettyAsterConfigClient.class);

    private final AsterClientProperties properties;
    private final Consumer<AsterMessage> changeHandler;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AtomicBoolean running = new AtomicBoolean(false);

    private EventLoopGroup group;
    private Channel channel;

    public NettyAsterConfigClient(AsterClientProperties properties, Consumer<AsterMessage> changeHandler) {
        this.properties = properties;
        this.changeHandler = changeHandler;
    }

    public void start() {
        if (!properties.isNettyEnabled() || !running.compareAndSet(false, true)) {
            return;
        }
        group = new NioEventLoopGroup();
        connect();
    }

    private void connect() {
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
                                .addLast(new NettyClientHandler());
                    }
                });

        bootstrap.connect(nettyHost(), properties.getNettyPort()).addListener(future -> {
            if (!running.get()) {
                return;
            }
            if (future.isSuccess()) {
                channel = ((io.netty.channel.ChannelFuture) future).channel();
                log.info("Connected to Aster Netty server, host={}, port={}", nettyHost(), properties.getNettyPort());
                channel.closeFuture().addListener(close -> scheduleReconnect());
                return;
            }
            log.warn("Failed to connect Aster Netty server, host={}, port={}", nettyHost(), properties.getNettyPort(), future.cause());
            scheduleReconnect();
        });
    }

    private void scheduleReconnect() {
        if (!running.get() || group == null || group.isShuttingDown()) {
            return;
        }
        group.schedule(this::connect, properties.getReconnectDelay().toMillis(), TimeUnit.MILLISECONDS);
    }

    private String nettyHost() {
        if (properties.getNettyHost() != null && !properties.getNettyHost().isBlank()) {
            return properties.getNettyHost();
        }
        return URI.create(properties.getServerAddr()).getHost();
    }

    @Override
    public void close() {
        running.set(false);
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @ChannelHandler.Sharable
    private class NettyClientHandler extends SimpleChannelInboundHandler<String> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.writeAndFlush(objectMapper.writeValueAsString(
                    AsterMessage.init(properties.getEnv(), properties.getNamespaces())) + "\n");
            super.channelActive(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String payload) throws Exception {
            AsterMessage message = objectMapper.readValue(payload, AsterMessage.class);
            if (message.type() == AsterMessageType.CONFIG_CHANGE_NOTIFY) {
                changeHandler.accept(message);
                return;
            }
            if (message.type() == AsterMessageType.INIT_ACK) {
                log.info("Aster Netty subscription acknowledged, env={}, namespaces={}, revision={}",
                        message.env(), message.namespaces(), message.revision());
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                ctx.writeAndFlush(objectMapper.writeValueAsString(AsterMessage.heartbeat()) + "\n");
                return;
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Aster Netty client connection failed", cause);
            ctx.close();
        }
    }
}
