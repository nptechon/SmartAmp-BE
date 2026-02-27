package com.nptechon.smartamp.tcp.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

public final class HexDumpUtil {

    private HexDumpUtil() {}

    public static String dump(ByteBuf buf) {
        // readerIndex ~ writerIndex 범위만
        return ByteBufUtil.hexDump(
                buf,
                buf.readerIndex(),
                buf.readableBytes()
        ).toUpperCase();
    }

    /**
     * 보기 좋게 16바이트 단위로 줄바꿈
     */
    public static String pretty(ByteBuf buf) {
        String hex = dump(buf);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < hex.length(); i += 32) { // 16 bytes = 32 hex chars
            int end = Math.min(i + 32, hex.length());
            sb.append(hex, i, end).append('\n');
        }
        return sb.toString();
    }
}
