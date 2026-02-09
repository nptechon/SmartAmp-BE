package com.nptechon.smartamp.tcp.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class FileFrameEncoder {

    public static final int FRAME_SIZE = 512;
    public static final int DATA_SIZE = 508;

    public static final byte START = 'S';
    public static final byte DATA  = 'D';
    public static final byte END   = 'E';

    private FileFrameEncoder() {}

    /** FS: 파일 메타데이터 포함 */
    public static ByteBuf encodeStart(
            ByteBufAllocator alloc,
            int totalSize,
            byte formatCode,
            String fileName
    ) {
        ByteBuf out = alloc.buffer(FRAME_SIZE, FRAME_SIZE);

        out.writeByte('F');
        out.writeByte('S');
        out.writeByte(0x00);
        out.writeByte(0x00);

        byte[] payload = new byte[DATA_SIZE];

        // totalSize (int32, Little Endian)
        payload[0] = (byte)( totalSize        & 0xFF);
        payload[1] = (byte)((totalSize >> 8 ) & 0xFF);
        payload[2] = (byte)((totalSize >> 16) & 0xFF);
        payload[3] = (byte)((totalSize >> 24) & 0xFF);

        payload[4] = formatCode;

        byte[] nameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 200)
            nameBytes = Arrays.copyOf(nameBytes, 200);

        payload[5] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, payload, 6, nameBytes.length);

        out.writeBytes(payload);
        return out;
    }

    /** FD: 파일 데이터 */
    /** FD: 파일 데이터 */
    public static ByteBuf encodeData(
            ByteBufAllocator alloc,
            int seq,
            byte[] payload508
    ) {
        if (payload508 == null || payload508.length != DATA_SIZE) {
            throw new IllegalArgumentException(
                    "payload must be exactly " + DATA_SIZE + " bytes"
            );
        }

        ByteBuf out = alloc.buffer(FRAME_SIZE, FRAME_SIZE);

        out.writeByte('F');
        out.writeByte('D');

        // seq Little Endian
        out.writeByte(seq & 0xFF);
        out.writeByte((seq >> 8) & 0xFF);

        out.writeBytes(payload508);
        return out;
    }


    /** FE */
    public static ByteBuf encodeEnd(ByteBufAllocator alloc) {
        ByteBuf out = alloc.buffer(FRAME_SIZE, FRAME_SIZE);
        out.writeByte('F');
        out.writeByte('E');
        out.writeByte(0x00);
        out.writeByte(0x00);
        out.writeZero(DATA_SIZE);
        return out;
    }
}

