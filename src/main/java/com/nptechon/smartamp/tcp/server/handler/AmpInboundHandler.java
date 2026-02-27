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

import java.util.List;

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
        log.info("INBOUND RAW size={}", frame.readableBytes());

        if (CommandPacketCodec.isCommand(frame)) {
            // 앰프에서 받은 패킷 디코딩
            CommandPacket p = CommandPacketCodec.decode(frame);
            // Opcode 에 따라서 처리
            handleCommand(ctx, p);
            return;
        }
        log.warn("unknown frame: first={}", frame.getUnsignedByte(frame.readerIndex()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Connection reset / timed out는 흔한 네트워크 종료 케이스라 info/warn 정도로만
        log.warn("TCP exception: remote={} ch={} cause={}",
                ctx.channel().remoteAddress(),
                ctx.channel().id(),
                cause.toString());

        // 세션 정리 + 채널 종료
        sessionManager.unbind(ctx.channel());
        ctx.close();
    }

    private void handleCommand(ChannelHandlerContext ctx, CommandPacket packet) {
        int ampId = packet.getDeviceId();
        int opcode = packet.getOpcode();
        byte[] payload = packet.getPayload();

        switch (opcode) {
            // DEVICE_REGISTER (Request)
            case 0x01 -> {
                sessionManager.bind(ampId, ctx.channel());
                log.info("---> Send Packet to Amp");
                log.info("---> Device Register Request ampId={} channel={}", ampId, ctx.channel().id());

                // ACK 서버 시간으로 DateTime7 채워서 보낸다
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

            // AMP_CONTROL_RESPONSE (0x82)
            case 0x82 -> {
                String powerResult;
                if (payload == null || payload.length < 1) {
                    log.warn("<--- Rcv Power Control response from amp... invalid payload ampId={}", ampId);
                    commandSender.completeControlExceptionally(
                            ampId, new IllegalArgumentException("control payload empty"));
                    return;
                }

                boolean isOn = (payload[0] & 0xFF) == 1;
                if ((payload[0] & 0xFF) == 1) {
                    powerResult = "on";
                } else { powerResult = "off"; }
                log.info("<--- Rcv Power Control response from amp... ampId={} power={}", ampId, powerResult);

                // 앰프에서 응답이 왔으니 미리 만들어둔 상자에 값을 넣어줌
                commandSender.completeControl(ampId, isOn);
            }

            // PREDEFINED_BROADCAST_RESPONSE (0x83)
            case 0x83 -> {
                if (payload == null || payload.length < 1) {
                    log.warn("<--- Rcv Predefined Broadcast response from amp... invalid payload ampId={}", ampId);
                    commandSender.completeBroadcastExceptionally(
                            ampId, new IllegalArgumentException("broadcast payload empty"));
                    return;
                }

                int result = payload[0] & 0xFF; // 0=OK, 1=Busy
                boolean isBusy = (result == 1);
                log.info("<--- Rcv Predefined Broadcast response from amp... ampId={} isBusy={}", ampId, isBusy);

                commandSender.completeBroadcast(ampId, result == 0);
            }

            // STREAM_TYPE_RESPONSE (0x84)
            case 0x84 -> {
                if (payload == null || payload.length < 1) {
                    log.warn("<--- Rcv Stream Type response from amp... invalid payload ampId={}", ampId);
                    commandSender.completeStreamExceptionally(
                            ampId, new IllegalArgumentException("stream payload empty"));
                    return;
                }

                int result = payload[0] & 0xFF; // 0=OK, 1=Busy
                boolean isBusy = (result == 1);
                log.info("<--- Rcv Stream Type response from amp... ampId={} isBusy={}", ampId, isBusy);

                commandSender.completeStream(ampId, result == 0);
            }

            // LOG_RESPONSE (0x85)
            case 0x85 -> {
                try {
                    log.info("<--- Rcv Log Response ampId={} payloadSize={}",
                            ampId, payload == null ? 0 : payload.length);

                    // 파싱하지 않고 그대로 전달
                    commandSender.completeLogPayload(ampId, payload);
                } catch (Exception e) {
                    commandSender.completeLogExceptionally(ampId, e);
                }
            }

            // AMP_STATUS_RESPONSE
            case 0x86 -> {
                String statusValue;

                if (payload == null || payload.length < 1) {
                    log.warn("<--- Rcv Amp Status response from amp... invalid payload ampId={} len={}",
                            ampId, payload == null ? -1 : payload.length);
                    commandSender.completeStatusExceptionally(
                            ampId, new IllegalArgumentException("status payload empty"));
                    return;
                }

                boolean isOn = (payload[0] & 0xFF) == 1;
                if ((payload[0] & 0xFF) == 1) {
                    statusValue = "on";
                } else { statusValue = "off"; }
                log.info("<--- Rcv Amp Status response from amp... ampId={} status={}", ampId, statusValue);

                // 여기서 대기중인 요청을 깨운다
                commandSender.completeStatus(ampId, isOn);
            }

            default -> {
                log.info("command opcode=0x{} from ampId={} payloadLen={}",
                        Integer.toHexString(opcode), ampId, packet.getPayload().length);
            }
        }
    }
}