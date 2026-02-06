package com.nptechon.smartamp.tcp.server.sender;

import com.nptechon.smartamp.tcp.server.session.TcpSessionManager;
import com.nptechon.smartamp.tcp.util.HexDumpUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileSender {

    private static final int TOTAL = 512;
    private static final int HDR = 4;
    private static final int DATA = 508;

    private final TcpSessionManager tcpSessionManager;

    /**
     * C# 규격 512 프레임 파일 전송
     * - pacing: realtime이면 2ms 정도 딜레이
     */
    public void sendMp3File(int ampId, Path mp3Path, byte formatCode, boolean realtime) throws java.io.IOException {
        Channel ch = tcpSessionManager.get(ampId);
        if (ch == null || !ch.isActive()) {
            throw new IllegalStateException("AMP not connected: " + ampId);
        }

        byte[] fileBytes = Files.readAllBytes(mp3Path);
        String fileName = mp3Path.getFileName().toString();
        int totalSize = fileBytes.length;
        int pacingMs = realtime ? 2 : 0;

        // blocking 작업은 별도 스레드
        new Thread(() -> {
            try {
                // 1) FS
                ByteBuf fs = buildFileStart(ch, fileName, totalSize, formatCode);
                log.info("[TX][FILE512][FS] ampId={} bytes=\n{}", ampId, HexDumpUtil.pretty(fs));
                ch.writeAndFlush(fs).syncUninterruptibly();
                if (pacingMs > 0) sleep(pacingMs);

                // 2) FD
                int offset = 0;
                int seq = 0;
                byte[] payload = new byte[DATA];

                while (offset < fileBytes.length) {
                    int remain = fileBytes.length - offset;
                    int copy = Math.min(DATA, remain);

                    System.arraycopy(fileBytes, offset, payload, 0, copy);
                    if (copy < DATA) Arrays.fill(payload, copy, DATA, (byte) 0x00);

                    ByteBuf fd = buildFileData(ch, seq, payload);
                    log.debug("[TX][FILE512][FD] ampId={} seq={} copy={}", ampId, seq, copy);
                    ch.writeAndFlush(fd).syncUninterruptibly();

                    offset += copy;
                    seq++;

                    if (pacingMs > 0) sleep(pacingMs);
                }

                // 3) FE
                ByteBuf fe = buildFileEnd(ch);
                log.info("[TX][FILE512][FE] ampId={} bytes=\n{}", ampId, HexDumpUtil.pretty(fe));
                ChannelFuture f = ch.writeAndFlush(fe);
                int finalSeq = seq;
                f.addListener(done -> {
                    if (done.isSuccess()) {
                        log.info("[TX][FILE512][DONE] ampId={} totalBytes={} frames={}", ampId, totalSize, finalSeq);
                    } else {
                        log.error("[TX][FILE512][FAIL] ampId={}", ampId, done.cause());
                    }
                });

            } catch (Exception e) {
                log.error("sendMp3File failed ampId={}", ampId, e);
            }
        }, "file512-sender-" + ampId).start();
    }

    // ===== C# FilePacketBuilder와 동일한 구조 =====

    private ByteBuf buildFileStart(Channel ch, String fileName, int totalSize, byte formatCode) {
        ByteBuf out = ch.alloc().buffer(TOTAL, TOTAL);

        out.writeByte('F');
        out.writeByte('S');
        out.writeByte(0x00);
        out.writeByte(0x00);

        byte[] payload = new byte[DATA];

        // totalSize int32 little-endian
        payload[0] = (byte) (totalSize & 0xFF);
        payload[1] = (byte) ((totalSize >> 8) & 0xFF);
        payload[2] = (byte) ((totalSize >> 16) & 0xFF);
        payload[3] = (byte) ((totalSize >> 24) & 0xFF);

        payload[4] = formatCode;

        byte[] nameBytes = (fileName == null ? "audio.mp3" : fileName).getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 200) nameBytes = Arrays.copyOf(nameBytes, 200);

        payload[5] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, payload, 6, nameBytes.length);

        out.writeBytes(payload);
        return out;
    }

    private ByteBuf buildFileData(Channel ch, int seq, byte[] payload508) {
        if (payload508 == null || payload508.length != DATA) {
            throw new IllegalArgumentException("payload must be exactly 508 bytes");
        }

        ByteBuf out = ch.alloc().buffer(TOTAL, TOTAL);

        out.writeByte('F');
        out.writeByte('D');

        // seq 2 bytes little-endian (C#과 동일)
        out.writeByte(seq & 0xFF);         // low
        out.writeByte((seq >> 8) & 0xFF);  // high

        out.writeBytes(payload508);
        return out;
    }

    private ByteBuf buildFileEnd(Channel ch) {
        ByteBuf out = ch.alloc().buffer(TOTAL, TOTAL);
        out.writeByte('F');
        out.writeByte('E');
        out.writeByte(0x00);
        out.writeByte(0x00);
        out.writeZero(DATA);
        return out;
    }

    private static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
