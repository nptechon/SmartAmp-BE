package com.nptechon.smartamp.tcp.codec;

import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import com.nptechon.smartamp.tcp.protocol.CommandPacket;
import com.nptechon.smartamp.tcp.protocol.AmpOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class CommandPacketCodec {

    /**
     * STX(0xAA) 기반 명령 패킷 식별 로직
     */

    public static boolean isCommand(ByteBuf frame) {
        return (frame.getUnsignedByte(frame.readerIndex()) == 0xAA);
    }

    /**
     * 디바이스 → 서버
     * ByteBuf → CommandPacket 디코딩
     */
    public static CommandPacket decode(ByteBuf frame) {
        int idx = frame.readerIndex();

        // [AA][LEN(LE2)][DEV][DT7][OP][PAYLOAD...][CRC][55]
        int len = frame.getUnsignedShortLE(idx + 1);
        int devId = frame.getUnsignedByte(idx + 3);

        byte[] dt = new byte[7];
        frame.getBytes(idx + 4, dt);

        int opcode = frame.getUnsignedByte(idx + 11);

        int payloadLen = len - (1 + 2 + 1 + 7 + 1 + 1 + 1);
        // stx + len2 + dev + dt7 + opcode + crc + etx
        if (payloadLen < 0) payloadLen = 0;

        byte[] payload = new byte[payloadLen];
        if (payloadLen > 0) {
            frame.getBytes(idx + 12, payload);
        }

        return CommandPacket.builder()
                .length(len)
                .deviceId(devId)
                .dateTime7(dt)
                .opcode(opcode)
                .payload(payload)
                .build();
    }

    /**
     * 서버 → 디바이스 영역!!
     * Command 패킷 만드는 도구
     * → 서버가 디바이스에 명령(0x02, 0x04 등) 보낼 때 필수!!
     * 실제 바이너리 포맷으로 패킷 조립 + Netty 에 맞게 ByteBuf 로 만들기
     */
    public static ByteBuf encode(
            ByteBufAllocator alloc,
            int deviceId,
            byte[] dateTime7,
            AmpOpcode opcode,
            byte[] payload
    ) {

        int payloadLen = (payload == null ? 0 : payload.length);
        switch (opcode) {
            case PLAY_INDEX_PREDEFINED, STREAM_TYPE -> {
                if (payloadLen != 2) {
                    throw new CustomException(ErrorCode.PROTOCOL_INVALID_LENGTH);
                }
            }
        }
        int len = 1 + 2 + 1 + 7 + 1 + payloadLen + 1 + 1;

        ByteBuf out = alloc.buffer(len);

        out.writeByte(0xAA);
        out.writeShortLE(len);
        out.writeByte(deviceId & 0xFF);

        if (dateTime7 == null || dateTime7.length != 7) {
            // 서버 기준 시간 넣고 싶으면 여기서 만들어 넣기
            dateTime7 = new byte[7];
        }

        out.writeBytes(dateTime7);
        out.writeByte(opcode.getCode() & 0xFF);

        if (payloadLen > 0) {
            out.writeBytes(payload);
        }

        out.writeByte(0x00); // CRC (형식)
        out.writeByte(0x55);

        return out;
    }
}
