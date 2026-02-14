package com.nptechon.smartamp.tcp.server;

import com.nptechon.smartamp.global.config.TcpServerProperties;
import com.nptechon.smartamp.tcp.codec.SmartAmpFrameDecoder;
import com.nptechon.smartamp.tcp.server.handler.AmpInboundHandler;
import com.nptechon.smartamp.tcp.server.sender.CommandSender;
import com.nptechon.smartamp.tcp.server.session.TcpSessionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NettyTcpServer {

    private final TcpServerProperties props;
    private final TcpSessionManager sessionManager;
    private final CommandSender commandSender;

    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;

    @PostConstruct
    public void start() throws InterruptedException {
        boss = new NioEventLoopGroup(props.getBossThreads());
        worker = (props.getWorkerThreads() <= 0)
                ? new NioEventLoopGroup()
                : new NioEventLoopGroup(props.getWorkerThreads());

        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new SmartAmpFrameDecoder());
                        p.addLast(new AmpInboundHandler(sessionManager, commandSender));
                    }
                });

        serverChannel = b.bind(props.getPort()).sync().channel();
        log.info("=== Netty TCP server started at port {} ===", props.getPort());
    }

    @PreDestroy
    public void stop() {
        try {
            if (serverChannel != null) serverChannel.close();
        } finally {
            if (worker != null) worker.shutdownGracefully();
            if (boss != null) boss.shutdownGracefully();
            log.info("=== Netty TCP server stopped ===");
        }
    }
}
