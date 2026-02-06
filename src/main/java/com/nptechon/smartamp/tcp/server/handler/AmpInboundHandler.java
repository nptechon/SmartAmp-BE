package com.nptechon.smartamp.tcp.server.handler;

import com.nptechon.smartamp.tcp.codec.CommandPacketCodec;
import com.nptechon.smartamp.tcp.protocol.AmpOpcode;
import com.nptechon.smartamp.tcp.protocol.CommandPacket;
import com.nptechon.smartamp.tcp.protocol.DateTime7;
import com.nptechon.smartamp.tcp.server.sender.CommandSender;
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
    private final CommandSender commandSender;

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

    private void handleCommand(ChannelHandlerContext ctx, CommandPacket packet) {
        int ampId = packet.getDeviceId();
        int opcode = packet.getOpcode();

        switch (opcode) {
            // DEVICE_REGISTER
            case 0x01 -> {
                sessionManager.bind(ampId, ctx.channel());
                log.info("device registered ampId={} channel={}", ampId, ctx.channel().id());

                // ACK는 서버 시간으로 DateTime7 채워서 보낸다
                byte[] serverDt7 = DateTime7.now();

                ByteBuf ack = CommandPacketCodec.encode(
                        ctx.alloc(),
                        ampId,
                        serverDt7,
                        AmpOpcode.DEVICE_REGISTER_ACK,   // 0x81
                        new byte[0]
                );
                ctx.writeAndFlush(ack);

            }

            // LOG_RESPONSE
            case 0x85 -> {
                log.info("log response from ampId={} payloadLen={}", ampId, packet.getPayload().length);
            }

            // AMP_STATUS_RESPONSE
            case 0x86 -> {
                byte[] payload = packet.getPayload();
                String statusValue;

                if (payload == null || payload.length < 1) {
                    log.warn("amp status response invalid payload ampId={} len={}",
                            ampId, payload == null ? -1 : payload.length);
                    // 대기중인 future가 있다면 예외로 깨줄 수도 있음(선택)
                    commandSender.completeStatusExceptionally(
                            ampId, new IllegalArgumentException("status payload empty"));
                    return;
                }

                int statusRaw = payload[0] & 0xFF;   // 0 or 1
                boolean isOn = (statusRaw == 1);
                if (statusRaw == 1) {
                    statusValue = "on";
                } else { statusValue = "off"; }

                log.info("amp status response ampId={} isOn={} raw={}", ampId, isOn, statusValue);

                // 여기서 대기중인 요청을 깨운다
                commandSender.completeStatus(ampId, statusValue);
            }


            default -> {
                log.info("command opcode=0x{} from ampId={} payloadLen={}",
                        Integer.toHexString(opcode), ampId, packet.getPayload().length);
            }
        }
    }
}