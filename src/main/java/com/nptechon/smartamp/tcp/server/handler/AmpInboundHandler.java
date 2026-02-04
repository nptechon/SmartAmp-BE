package com.nptechon.smartamp.tcp.server.handler;

import com.nptechon.smartamp.tcp.codec.CommandPacketCodec;
import com.nptechon.smartamp.tcp.protocol.AmpOpcode;
import com.nptechon.smartamp.tcp.protocol.CommandPacket;
import com.nptechon.smartamp.tcp.protocol.DateTime7;
import com.nptechon.smartamp.tcp.server.session.TcpSessionManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AmpInboundHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final TcpSessionManager sessionManager;

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        sessionManager.unbind(ctx.channel());
        log.info("channel inactive: {}", ctx.channel().id());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        if (CommandPacketCodec.isCommand(frame)) {
            CommandPacket p = CommandPacketCodec.decode(frame);
            handleCommand(ctx, p);
            return;
        }
        log.warn("unknown frame: first={}", frame.getUnsignedByte(frame.readerIndex()));
    }

    private void handleCommand(ChannelHandlerContext ctx, CommandPacket p) {
        int devId = p.getDeviceId();
        int op = p.getOpcode();

        switch (op) {
            case 0x01 -> { // DEVICE_REGISTER
                sessionManager.bind(devId, ctx.channel());
                log.info("device registered devId={} ch={}", devId, ctx.channel().id());

                // ACK는 서버 시간으로 DateTime7 채워서 보낸다
                byte[] serverDt7 = DateTime7.now();

                ByteBuf ack = CommandPacketCodec.encode(
                        ctx.alloc(),
                        devId,
                        serverDt7,
                        AmpOpcode.DEVICE_REGISTER_ACK,   // 0x81
                        new byte[0]
                );
                ctx.writeAndFlush(ack);

            }

            case 0x85 -> {
                log.info("log response from devId={} payloadLen={}", devId, p.getPayload().length);
            }

            default -> {
                log.info("command opcode=0x{} from devId={} payloadLen={}",
                        Integer.toHexString(op), devId, p.getPayload().length);
            }
        }
    }
}