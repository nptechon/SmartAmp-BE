package com.nptechon.smartamp.tcp.server.sender;

import com.nptechon.smartamp.tcp.codec.CommandPacketCodec;
import com.nptechon.smartamp.tcp.protocol.AmpOpcode;
import com.nptechon.smartamp.tcp.protocol.DateTime7;
import com.nptechon.smartamp.tcp.protocol.payload.AmpPower;
import com.nptechon.smartamp.tcp.server.session.TcpSessionManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommandSender {

    private final TcpSessionManager sessionManager;

    public void sendPower(int ampId, AmpPower power) {
        Channel channel = sessionManager.get(ampId);
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("AMP not connected: " + ampId);
        }

        byte[] dt7 = DateTime7.now();

        // payload는 1바이트짜리 배열
        byte[] payload = new byte[] { power.getValue() };

        ByteBuf pkt = CommandPacketCodec.encode(
                channel.alloc(),
                ampId,
                dt7,
                AmpOpcode.AMP_CONTROL,
                payload
        );

        channel.writeAndFlush(pkt);
    }


    public void requestLogs(int ampId) {
        Channel ch = sessionManager.get(ampId);
        if (ch == null || !ch.isActive()) throw new IllegalStateException("AMP not connected: " + ampId);

        byte[] dt7 = new byte[7];
        ByteBuf pkt = CommandPacketCodec.encode(ch.alloc(), ampId, dt7, AmpOpcode.LOG_REQUEST, new byte[0]);
        ch.writeAndFlush(pkt);
    }
}
