package com.nptechon.smartamp.tcp.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class SmartAmpFrameDecoder extends ByteToMessageDecoder {

    private static final short CMD_STX = (short) 0xAA;
    private static final short CMD_ETX = (short) 0x55;

    private static final short STRM_STX = (short) 0x46; // 'F'
    private static final int STREAM_FRAME_SIZE = 512;

    // Command 최소 길이: [AA][LEN2][DEV][DT7][OP][CRC][55] = 1+2+1+7+1+1+1 = 14
    private static final int CMD_MIN_LEN = 14;
    private static final int CMD_MAX_LEN = 4096;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() > 0) {
            int idx = in.readerIndex();
            short b0 = in.getUnsignedByte(idx);
            log.info("[DECODE] readable={} first=0x{}", in.readableBytes(), Integer.toHexString(b0));
        }
        while (true) {
            if (in.readableBytes() < 1) return;

            int idx = in.readerIndex();
            short first = in.getUnsignedByte(idx);

            // 1) Stream frame: fixed 512
            // 기존 코드는 first == 'F' 만으로 512바이트를 잘라서 out 에 넣었음
            //   → 만약 프레임 동기가 깨진 상태에서 우연히 0x46을 만나면 stream으로 오인하고 512바이트 discard
            //   → 이후 모든 프레임이 깨져서 timeout 연쇄가 발생 가능
            //
            // 스트림 프레임은 반드시 [0x46]['S'/'D'/'E'] 로 시작한다고 했으니 2바이트 이상 확인
            if (first == STRM_STX) {
                if (in.readableBytes() < 2) return; // 헤더 2바이트는 있어야 판별 가능

                short second = in.getUnsignedByte(idx + 1); // 'S'/'D'/'E' expected
                boolean isStreamHeader = (second == (short) 'S' || second == (short) 'D' || second == (short) 'E');

                if (isStreamHeader) {
                    if (in.readableBytes() < STREAM_FRAME_SIZE) {
                        return;
                    }
                    out.add(in.readRetainedSlice(STREAM_FRAME_SIZE));
                    continue;
                }

                // 'F' 이지만 FS/FD/FE가 아니면 스트림 프레임이 아님 → 1바이트 버리고 동기 재시도
                in.readByte();
                continue;
            }

            // 2) Command frame: [AA][LEN(LE2)]...
            if (first == CMD_STX) {
                if (in.readableBytes() < 1 + 2) { // STX + LEN(2)
                    return;
                }

                int len = in.getUnsignedShortLE(idx + 1); // 전체 길이
                log.info("[DECODE] len={}", len);

                // 길이 sanity check 강화
                if (len < CMD_MIN_LEN || len > CMD_MAX_LEN) {
                    // 이상 프레임이면 1바이트 버리고 다시 동기
                    in.readByte();
                    continue;
                }

                if (in.readableBytes() < len) {
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
