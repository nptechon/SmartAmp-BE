package com.nptechon.smartamp.broadcast.voice.controller;

import com.nptechon.smartamp.broadcast.voice.dto.VoiceBroadcastResultDto;
import com.nptechon.smartamp.broadcast.voice.service.VoiceConvertService;
import com.nptechon.smartamp.global.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/voice")
public class VoiceController {

    private final VoiceConvertService voiceConvertService;

    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<VoiceBroadcastResultDto>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ampId") int ampId,
            HttpServletRequest request
    ) {
        VoiceBroadcastResultDto result = voiceConvertService.uploadAndBroadcast(file, ampId);

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "ok",
                        "음성 직접 방송 성공",
                        result,
                        request.getRequestId(),
                        request.getRequestURI()
                )
        );
    }
}
