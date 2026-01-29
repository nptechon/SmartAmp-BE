package com.nptechon.smartamp.controller;

import com.nptechon.smartamp.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class VoiceController {

    private final UploadProperties uploadProperties;

    @PostMapping("/api/voice/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ampId") String ampId
    ) throws IOException {

        log.info("들어옴");
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "file is empty"
            ));
        }

        Path dir = Paths.get(uploadProperties.getDir());
        Files.createDirectories(dir);

        String safeOriginalName = (file.getOriginalFilename() == null)
                ? "voice.wav"
                : file.getOriginalFilename().replaceAll("[\\\\/]", "_");

        String filename = System.currentTimeMillis() + "_" + safeOriginalName;
        Path target = dir.resolve(filename);

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "ampId", ampId,
                "savedAs", filename,
                "size", file.getSize()
        ));
    }
}