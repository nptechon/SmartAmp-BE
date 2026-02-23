package com.nptechon.smartamp.broadcast.voice.service;

import com.nptechon.smartamp.broadcast.voice.dto.VoiceBroadcastResultDto;
import com.nptechon.smartamp.global.config.UploadProperties;
import com.nptechon.smartamp.global.error.CustomException;
import com.nptechon.smartamp.global.error.ErrorCode;
import com.nptechon.smartamp.tcp.protocol.payload.StreamType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceConvertService {

    private final UploadProperties uploadProperties;
    private final VoiceBroadcastService voiceBroadcastService;

    /**
     * EC2 프리티어 안정 운영용: 동시 변환 1개로 제한
     */
    private static final int MAX_CONCURRENT_CONVERSIONS = 1;
    private static final Semaphore CONVERT_SEMAPHORE =
            new Semaphore(MAX_CONCURRENT_CONVERSIONS, true);
    private static final long ACQUIRE_TIMEOUT_SECONDS = 10;




    public VoiceBroadcastResultDto uploadAndBroadcast(MultipartFile file, int ampId, int repeat) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.VOICE_FILE_EMPTY);
        }

        boolean acquired = false;

        Path dir = ensureUploadDir(); // 디렉터리 먼저 확보(실패 시 바로 예외)
        String mp3Name = System.currentTimeMillis() + "_voice.mp3";
        Path targetMp3 = dir.resolve(mp3Name);

        long startMs = System.currentTimeMillis();

        try {
            acquired = acquirePermit();

            try (InputStream wavStream = file.getInputStream()) {

                log.info("convert start: ampId={}, origName={}, size={}",
                        ampId, file.getOriginalFilename(), file.getSize());

                // 1) WAV -> MP3
                convertToMp3AndSave(wavStream, targetMp3);

                long mp3Size = Files.size(targetMp3);
                long tookMs = System.currentTimeMillis() - startMs;

                log.info("convert done: ampId={}, savedAs={}, mp3Size={}, tookMs={}",
                        ampId, mp3Name, mp3Size, tookMs);

                // 2) 변환 성공 -> 앰프로 파일 전송 시작 (비동기)
                voiceBroadcastService.sendMp3AsFile512(ampId, targetMp3, StreamType.MIC, repeat);
                log.info("file512 send started: ampId={}, mp3={}", ampId, targetMp3);

                // 전송 요청 성공 후 바로 삭제
                safeDelete(targetMp3);
                log.info("file delete: mp3={}", targetMp3);

                return new VoiceBroadcastResultDto(ampId, mp3Name, mp3Size, tookMs, 0x01, targetMp3);
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("voice upload interrupted: ampId={}", ampId, ie);

            safeDelete(targetMp3);
            throw new CustomException(
                    ErrorCode.VOICE_UPLOAD_INTERRUPTED,
                    "interrupted: " + ie.getMessage()
            );

        } catch (CustomException ce) {
            // 서비스 내부에서 이미 ErrorCode로 던진 경우는 그대로 전달
            safeDelete(targetMp3);
            throw ce;

        } catch (IllegalStateException e) {
            throw new CustomException(
                    ErrorCode.VOICE_CONVERT_FAILED,
                    e.getMessage(),
                    e
            );
        } catch (Exception e) {
            log.error("voice upload/convert/broadcast failed: ampId={}", ampId, e);

            safeDelete(targetMp3);
            throw new CustomException(
                    ErrorCode.VOICE_CONVERT_FAILED,
                    e.getMessage()
            );

        } finally {
            if (acquired) {
                CONVERT_SEMAPHORE.release();
                log.debug("permit released: availablePermits={}", CONVERT_SEMAPHORE.availablePermits());
            }
        }
    }

    private boolean acquirePermit() {
        try {
            boolean acquired = CONVERT_SEMAPHORE.tryAcquire(
                    ACQUIRE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            if (!acquired) {
                throw new CustomException(ErrorCode.VOICE_CONVERT_BUSY);
            }
            return true;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CustomException(
                    ErrorCode.VOICE_UPLOAD_INTERRUPTED,
                    "interrupted while waiting for conversion slot"
            );
        }
    }

    private Path ensureUploadDir() {
        Path dir = Paths.get(uploadProperties.getDir());
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new CustomException(
                    ErrorCode.VOICE_CONVERT_FAILED,
                    "failed to create upload dir: " + e.getMessage()
            );
        }
    }

    private void safeDelete(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (Exception ignore) {}
    }

    /**
     * WAV → MP3 변환 (ffmpeg)
     */
    private void convertToMp3AndSave(InputStream wavStream, Path outputMp3)
            throws IOException, InterruptedException {

        List<String> cmd = List.of(
                uploadProperties.getFfmpegPath(),
                "-y",
                "-hide_banner",
                "-loglevel", "error",
                "-f", "wav",
                "-i", "pipe:0",
                "-vn",
                "-ac", "1",
                "-b:a", "96k",
                "-codec:a", "libmp3lame",
                outputMp3.toAbsolutePath().toString()
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();

        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        Thread errThread = new Thread(() -> {
            try (InputStream es = p.getErrorStream()) {
                es.transferTo(errBuf);
            } catch (IOException ignored) {}
        }, "ffmpeg-stderr-reader");
        errThread.start();

        try (OutputStream ffmpegIn = p.getOutputStream()) {
            wavStream.transferTo(ffmpegIn);
        }

        boolean finished = p.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException("ffmpeg timeout");
        }

        errThread.join(2000);

        if (p.exitValue() != 0) {
            throw new IOException("ffmpeg failed: " + errBuf.toString());
        }

        if (!Files.exists(outputMp3) || Files.size(outputMp3) == 0) {
            throw new IOException("mp3 output not created or empty");
        }
    }
}
