package com.nptechon.smartamp.tcp.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * Stream 512 프레임 만드는 도구
 * 0x46 + 'S/D/E' + seq + 508 bytes 만들기
 * 서버가 MP3 스트리밍 프레임을 보낼 때 필수!!
 */
public class StreamFrameEncoder {

    private static final int FRAME_SIZE = 512;
    private static final int DATA_SIZE = 508;

    public static ByteBuf encode(
            ByteBufAllocator alloc,
            char type,
            int seq,
            byte[] data508OrNull
    ) {
        ByteBuf out = alloc.buffer(FRAME_SIZE);

        out.writeByte(0x46);              // 'F'
        out.writeByte((byte) type);       // 'S' / 'D' / 'E'
        out.writeShort(seq & 0xFFFF);     // Big Endian (default)

        if (data508OrNull == null || data508OrNull.length == 0) {
            // No Stream Data -> 508 bytes 0x00
            out.writeZero(DATA_SIZE);
            return out;
        }

        if (data508OrNull.length == DATA_SIZE) {
            out.writeBytes(data508OrNull);
        } else if (data508OrNull.length < DATA_SIZE) {
            out.writeBytes(data508OrNull);
            out.writeZero(DATA_SIZE - data508OrNull.length); // padding: 0x00
        } else {
            // 안전: 508 이상이면 잘라서 넣음
            out.writeBytes(data508OrNull, 0, DATA_SIZE);
        }

        return out;
    }
}
