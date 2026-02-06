package com.nptechon.smartamp.broadcast.controller;

import com.nptechon.smartamp.broadcast.service.VoiceBroadcastService;
import com.nptechon.smartamp.global.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/voice")
public class VoiceController {

    private final UploadProperties uploadProperties;
    private final VoiceBroadcastService voiceBroadcastService;

    /**
     * EC2 프리티어 안정 운영용: 동시 변환 1개로 제한
     */
    private static final int MAX_CONCURRENT_CONVERSIONS = 1;
    private static final Semaphore CONVERT_SEMAPHORE =
            new Semaphore(MAX_CONCURRENT_CONVERSIONS, true);
    private static final long ACQUIRE_TIMEOUT_SECONDS = 10;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ampId") int ampId
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "file is empty"
            ));
        }

        boolean acquired;
        try {
            acquired = CONVERT_SEMAPHORE.tryAcquire(
                    ACQUIRE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "interrupted while waiting for conversion slot"
            ));
        }

        if (!acquired) {
            return ResponseEntity.status(429).body(Map.of(
                    "success", false,
                    "message", "server is busy converting audio, try again later"
            ));
        }

        Path dir = Paths.get(uploadProperties.getDir());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            CONVERT_SEMAPHORE.release();
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "failed to create upload dir"
            ));
        }

        String mp3Name = System.currentTimeMillis() + "_voice.mp3";
        Path targetMp3 = dir.resolve(mp3Name);

        long startMs = System.currentTimeMillis();

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
            voiceBroadcastService.sendMp3AsFile512(ampId, targetMp3);
            log.info("file512 send started: ampId={}, mp3={}", ampId, targetMp3);

            // 응답
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "ampId", ampId,
                    "savedAs", mp3Name,
                    "size", mp3Size,
                    "tookMs", tookMs,
                    "send", "file512-started",
                    "formatCode", 0x01
            ));

        } catch (InterruptedException ie) {
            // 인터럽트 복구 (중요)
            Thread.currentThread().interrupt();
            log.warn("voice upload interrupted: ampId={}", ampId, ie);

            try { Files.deleteIfExists(targetMp3); } catch (Exception ignore) {}

            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "interrupted",
                    "detail", ie.getMessage()
            ));

        } catch (Exception e) {
            log.error("voice upload/convert/broadcast failed: ampId={}", ampId, e);

            try { Files.deleteIfExists(targetMp3); } catch (Exception ignore) {}

            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "upload or broadcast failed",
                    "detail", e.getMessage()
            ));

        } finally {
            CONVERT_SEMAPHORE.release();
            log.debug("permit released: availablePermits={}", CONVERT_SEMAPHORE.availablePermits());
        }
    }


    /**
     * WAV → MP3 변환
     */
    private void convertToMp3AndSave(
            InputStream wavStream,
            Path outputMp3
    ) throws IOException, InterruptedException {

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
            throw new IOException(
                    "ffmpeg failed: " + errBuf.toString(StandardCharsets.UTF_8)
            );
        }

        if (!Files.exists(outputMp3) || Files.size(outputMp3) == 0) {
            throw new IOException("mp3 output not created or empty");
        }
    }
}
