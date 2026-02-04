package com.nptechon.smartamp.tcp.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class SmartAmpFrameDecoder extends ByteToMessageDecoder {

    private static final short CMD_STX = (short) 0xAA;
    private static final short CMD_ETX = (short) 0x55;

    private static final short STRM_STX = (short) 0x46; // 'F'
    private static final int STREAM_FRAME_SIZE = 512;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (true) {
            if (in.readableBytes() < 1) return;

            in.markReaderIndex();
            short first = in.getUnsignedByte(in.readerIndex());

            // 1) Stream frame: fixed 512
            if (first == STRM_STX) {
                if (in.readableBytes() < STREAM_FRAME_SIZE) {
                    in.resetReaderIndex();
                    return;
                }
                out.add(in.readRetainedSlice(STREAM_FRAME_SIZE));
                continue;
            }

            // 2) Command frame: [AA][LEN(LE2)]...
            if (first == CMD_STX) {
                if (in.readableBytes() < 1 + 2) { // STX + LEN(2)
                    in.resetReaderIndex();
                    return;
                }

                int idx = in.readerIndex();
                int len = in.getUnsignedShortLE(idx + 1); // 전체 길이

                if (len <= 0 || len > 4096) {
                    // 이상 프레임이면 1바이트 버리고 다시 동기
                    in.readByte();
                    continue;
                }

                if (in.readableBytes() < len) {
                    in.resetReaderIndex();
                    return;
                }

                // ETX 확인
                short etx = in.getUnsignedByte(idx + len - 1);
                if (etx != CMD_ETX) {
                    // STX는 맞는데 ETX가 아니면 동기 깨진 것 → 1바이트 버리고 재시도
                    in.readByte();
                    continue;
                }

                out.add(in.readRetainedSlice(len));
                continue;
            }

            // unknown leading byte: sync 맞추기 위해 1바이트 discard
            in.readByte();
        }
    }
}
