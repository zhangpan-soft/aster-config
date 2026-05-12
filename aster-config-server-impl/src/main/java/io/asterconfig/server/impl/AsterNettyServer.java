package io.asterconfig.server.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsterNettyServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AsterNettyServer.class);

    private final AsterNettyServerProperties properties;
    private final AsterNettyServerHandler handler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public AsterNettyServer(AsterNettyServerProperties properties, AsterNettyServerHandler handler) {
        this.properties = properties;
        this.handler = handler;
    }

    @Override
    public void start() {
        if (!properties.isEnabled() || !running.compareAndSet(false, true)) {
            return;
        }
        bossGroup = new NioEventLoopGroup(properties.getBossThreads());
        workerGroup = new NioEventLoopGroup(properties.getWorkerThreads());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(properties.getIdleTimeoutSeconds(), 0, 0, TimeUnit.SECONDS))
                                    .addLast(new LineBasedFrameDecoder(1024 * 1024))
                                    .addLast(new StringDecoder(CharsetUtil.UTF_8))
                                    .addLast(new StringEncoder(CharsetUtil.UTF_8))
                                    .addLast(handler);
                        }
                    });
            serverChannel = bootstrap.bind(properties.getPort()).sync().channel();
            log.info("Aster Netty server started, port={}", properties.getPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
            throw new IllegalStateException("Aster Netty server start interrupted", e);
        } catch (Exception e) {
            running.set(false);
            stop();
            throw new IllegalStateException("Failed to start Aster Netty server", e);
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Aster Netty server stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
