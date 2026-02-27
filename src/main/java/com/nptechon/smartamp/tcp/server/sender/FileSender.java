package com.nptechon.smartamp.tcp.server.sender;

import com.nptechon.smartamp.tcp.codec.FileFrameEncoder;
import com.nptechon.smartamp.tcp.server.session.TcpSessionManager;
import com.nptechon.smartamp.tcp.util.HexDumpUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileSender {

    private static final int DATA = FileFrameEncoder.DATA_SIZE; // 508

    private final TcpSessionManager tcpSessionManager;

    /**
     * 512 프레임 파일 전송
     * - FS: 파일 메타(totalSize LE4 + formatCode + fileName)
     * - FD: 508B payload, seq(LE 2B)
     * - FE
     *
     * @param formatCode 0x01=MP3 등
     * @param realtime   true면 pacing(2ms)
     */
    public void sendMp3File(int ampId, Path mp3Path, byte formatCode, boolean realtime) throws IOException {
        Channel ch = tcpSessionManager.get(ampId);
        if (ch == null || !ch.isActive()) {
            throw new IllegalStateException("AMP not connected: " + ampId);
        }

        // 파일 로딩(기존 동작 유지: 메모리에 전체 로드)
        byte[] fileBytes = Files.readAllBytes(mp3Path);
        String fileName = mp3Path.getFileName().toString();
        int totalSize = fileBytes.length;

        int pacingMs = realtime ? 2 : 0;

        // blocking 작업은 별도 스레드
        new Thread(() -> {
            try {
                // 1) FS
                ByteBuf fs = FileFrameEncoder.encodeStart(
                        ch.alloc(),
                        totalSize,
                        formatCode,
                        (fileName == null || fileName.isBlank()) ? "audio.mp3" : fileName
                );

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

                    // payload 채우기 + padding
                    System.arraycopy(fileBytes, offset, payload, 0, copy);
                    if (copy < DATA) Arrays.fill(payload, copy, DATA, (byte) 0x00);

                    // FD 프레임 생성
                    ByteBuf fd = FileFrameEncoder.encodeData(ch.alloc(), seq, payload);

                    // 너무 로그 많으면 debug 유지 (필요하면 HexDumpUtil.pretty(fd)도 가능)
                    log.debug("[TX][FILE512][FD] ampId={} seq={} copy={}", ampId, seq, copy);

                    // 완료까지 대기(기존 동작 유지)
                    ch.writeAndFlush(fd).syncUninterruptibly();

                    offset += copy;
                    seq++;

                    if (pacingMs > 0) sleep(pacingMs);
                }

                // 3) FE
                ByteBuf fe = FileFrameEncoder.encodeEnd(ch.alloc());
                log.info("[TX][FILE512][FE] ampId={} bytes=\n{}", ampId, HexDumpUtil.pretty(fe));

                int finalSeq = seq; // listener에서 쓰려고 캡처
                ChannelFuture f = ch.writeAndFlush(fe);

                f.addListener(done -> {
                    if (done.isSuccess()) {
                        log.info("[TX][FILE512][DONE] ampId={} totalBytes={} frames={}",
                                ampId, totalSize, finalSeq);
                    } else {
                        log.error("[TX][FILE512][FAIL] ampId={}", ampId, done.cause());
                    }
                });

            } catch (Exception e) {
                log.error("sendMp3File failed ampId={}", ampId, e);
            }
        }, "file512-sender-" + ampId).start();
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
